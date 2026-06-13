import {
  clearSignaling,
  observeIceCandidates,
  observeSignalingAnswer,
  observeSignalingOffers,
  writeIceCandidate,
  writeSignalingAnswer,
  writeSignalingOffer,
} from './firebase-file-share.js';

const CHUNK_SIZE = 1 * 1024 * 1024;
const SEQ_HEADER_SIZE = 4;
const BUFFER_HIGH_THRESHOLD = 8 * 1024 * 1024;
const PROGRESS_INTERVAL_MS = 50; // 20fps max
const RTC_CONFIG = {
  iceServers: [{ urls: [
    'stun:stun.l.google.com:19302',
    'stun:stun1.l.google.com:19302',
    'stun:stun2.l.google.com:19302',
    'stun:stun3.l.google.com:19302',
    'stun:stun4.l.google.com:19302',
  ] }],
};

function createPeerConnection() {
  if (!window.RTCPeerConnection) {
    throw new Error('WebRTC is not supported in this browser');
  }
  return new RTCPeerConnection(RTC_CONFIG);
}

function waitForDataChannelOpen(channel) {
  if (channel.readyState === 'open') return Promise.resolve();
  return new Promise((resolve, reject) => {
    channel.onopen = () => resolve();
    channel.onerror = () => reject(new Error('Data channel failed to open'));
  });
}

async function waitForBufferedAmount(channel, limit = BUFFER_HIGH_THRESHOLD) {
  if (channel.bufferedAmount <= limit) return;
  await new Promise((resolve) => {
    channel.bufferedAmountLowThreshold = limit;
    channel.onbufferedamountlow = () => resolve();
  });
}

export class WebRTCFileSeeder {
  constructor({ roomCode, file, seederId, onPeerProgress, onPeerState }) {
    this.roomCode = roomCode;
    this.file = file;
    this.seederId = seederId;
    this.onPeerProgress = onPeerProgress || (() => {});
    this.onPeerState = onPeerState || (() => {});
    this.peers = new Map();
    this.unsubOffers = null;
  }

  start() {
    if (!this.file) throw new Error('No file selected to share');
    this.unsubOffers = observeSignalingOffers(this.roomCode, (peerId, offer) => {
      if (peerId === this.seederId || this.peers.has(peerId)) return;
      this.acceptPeer(peerId, offer).catch((error) => {
        console.error('[file-share] Seeder peer failed:', error);
        this.onPeerState(peerId, error.message || 'Could not connect');
      });
    });
  }

  async acceptPeer(peerId, offer) {
    const pc = createPeerConnection();
    const cleanups = [];
    let channel = null;

    this.peers.set(peerId, { pc, cleanups });
    this.onPeerState(peerId, 'Connecting');

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        writeIceCandidate(this.roomCode, peerId, 'seeder', event.candidate).catch(console.error);
      }
    };

    pc.ondatachannel = (event) => {
      channel = event.channel;
      channel.binaryType = 'arraybuffer';
      channel.onopen = () => this.sendFile(peerId, channel).catch((error) => {
        console.error('[file-share] Send failed:', error);
        this.onPeerState(peerId, error.message || 'Send failed');
      });
    };

    cleanups.push(observeIceCandidates(this.roomCode, peerId, 'leecher', (candidate) => {
      pc.addIceCandidate(new RTCIceCandidate(candidate)).catch(console.error);
    }));

    await pc.setRemoteDescription(new RTCSessionDescription(offer));
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    await writeSignalingAnswer(this.roomCode, peerId, answer);

    if (channel) await waitForDataChannelOpen(channel);
  }

  async sendFile(peerId, channel) {
    this.onPeerState(peerId, 'Sending');
    channel.send(JSON.stringify({
      type: 'header',
      fileName: this.file.name,
      fileSize: this.file.size,
      totalChunks: Math.ceil(this.file.size / CHUNK_SIZE),
    }));

    let offset = 0;
    let chunkIndex = 0;
    let lastProgressTime = 0;
    while (offset < this.file.size && channel.readyState === 'open') {
      const end = Math.min(offset + CHUNK_SIZE, this.file.size);
      const fileData = await this.file.slice(offset, end).arrayBuffer();
      const fileBytes = new Uint8Array(fileData);
      // Build payload: 4-byte big-endian seq number + file data
      const payload = new Uint8Array(SEQ_HEADER_SIZE + fileBytes.byteLength);
      payload[0] = (chunkIndex >>> 24) & 0xFF;
      payload[1] = (chunkIndex >>> 16) & 0xFF;
      payload[2] = (chunkIndex >>> 8) & 0xFF;
      payload[3] = chunkIndex & 0xFF;
      payload.set(fileBytes, SEQ_HEADER_SIZE);
      channel.send(payload.buffer);
      offset = end;
      chunkIndex++;
      const now = performance.now();
      if (now - lastProgressTime > PROGRESS_INTERVAL_MS) {
        this.onPeerProgress(peerId, offset, this.file.size);
        lastProgressTime = now;
      }
      await waitForBufferedAmount(channel);
    }

    if (channel.readyState === 'open') {
      channel.send(JSON.stringify({ type: 'complete' }));
      this.onPeerProgress(peerId, this.file.size, this.file.size);
      this.onPeerState(peerId, 'Complete');
    }
  }

  async stop() {
    if (this.unsubOffers) {
      this.unsubOffers();
      this.unsubOffers = null;
    }
    for (const [peerId, peer] of this.peers.entries()) {
      peer.cleanups.forEach((cleanup) => cleanup());
      peer.pc.close();
      await clearSignaling(this.roomCode, peerId).catch(() => {});
    }
    this.peers.clear();
  }
}

export class WebRTCFileLeecher {
  constructor({ roomCode, userId, fileName, fileSize, onProgress, onState }) {
    this.roomCode = roomCode;
    this.userId = userId;
    this.peerId = `${userId}-${crypto.randomUUID()}`;
    this.fileName = fileName;
    this.fileSize = fileSize;
    this.onProgress = onProgress || (() => {});
    this.onState = onState || (() => {});
    this.pc = null;
    this.channel = null;
    this.cleanups = [];
    this.receivedBytes = 0;
    this.receivedChunks = [];
    this.writable = null;
    this.fileHandle = null;
    this.writeQueue = [];
    this.draining = false;
    this.nextExpectedSeq = 0;
    this.reorderBuffer = new Map();
    this.completeReceived = false;
    this.lastProgressTime = 0;
    this.useFileSystemAccess = Boolean(window.showSaveFilePicker);
  }

  async start() {
    await this.prepareSaveTarget();

    this.pc = createPeerConnection();
    this.channel = this.pc.createDataChannel('movsync-file', { ordered: false, maxRetransmits: 0 });
    this.channel.binaryType = 'arraybuffer';

    this.pc.onicecandidate = (event) => {
      if (event.candidate) {
        writeIceCandidate(this.roomCode, this.peerId, 'leecher', event.candidate).catch(console.error);
      }
    };

    const result = new Promise((resolve, reject) => {
      this.channel.onopen = () => this.onState('Connected');
      this.channel.onerror = () => reject(new Error('P2P data channel failed'));
      this.channel.onmessage = (event) => {
        this.handleMessage(event.data, resolve, reject).catch(reject);
      };
    });

    this.cleanups.push(observeSignalingAnswer(this.roomCode, this.peerId, async (answer) => {
      if (this.pc?.remoteDescription) return;
      await this.pc.setRemoteDescription(new RTCSessionDescription(answer));
    }));

    this.cleanups.push(observeIceCandidates(this.roomCode, this.peerId, 'seeder', (candidate) => {
      this.pc.addIceCandidate(new RTCIceCandidate(candidate)).catch(console.error);
    }));

    this.onState('Connecting');
    const offer = await this.pc.createOffer();
    await this.pc.setLocalDescription(offer);
    await writeSignalingOffer(this.roomCode, this.peerId, offer);

    return result.finally(() => this.cleanup());
  }

  async prepareSaveTarget() {
    if (!this.useFileSystemAccess) return;
    this.fileHandle = await window.showSaveFilePicker({
      suggestedName: this.fileName || 'movsync-video',
      types: [{
        description: 'Video file',
        accept: { 'video/*': ['.mp4', '.mkv', '.webm', '.avi', '.mov'] },
      }],
    });
    this.writable = await this.fileHandle.createWritable();
  }

  async handleMessage(data, resolve, reject) {
    if (typeof data === 'string') {
      const message = JSON.parse(data);
      if (message.type === 'header') {
        this.fileName = message.fileName || this.fileName;
        this.fileSize = Number(message.fileSize || this.fileSize || 0);
        this.onState(`Receiving ${this.fileName}`);
      } else if (message.type === 'complete') {
        this.completeReceived = true;
        if (this.reorderBuffer.size === 0) {
          await this.drainWriteQueue();
          const file = await this.finishDownload();
          resolve(file);
        }
      }
      return;
    }

    const raw = data instanceof ArrayBuffer ? new Uint8Array(data) : new Uint8Array(await data.arrayBuffer());
    if (raw.byteLength < SEQ_HEADER_SIZE) return;

    // Extract 4-byte big-endian sequence number
    const seq = (raw[0] << 24) | (raw[1] << 16) | (raw[2] << 8) | raw[3];
    const chunk = raw.slice(SEQ_HEADER_SIZE).buffer;
    this.receivedBytes += chunk.byteLength;

    // Reorder: write sequentially, buffer out-of-order chunks
    if (seq === this.nextExpectedSeq) {
      if (this.writable) {
        this.writeQueue.push(chunk);
        this.scheduleWriteDrain();
      } else {
        this.receivedChunks.push(chunk);
      }
      this.nextExpectedSeq++;
      // Flush consecutive buffered chunks
      while (this.reorderBuffer.has(this.nextExpectedSeq)) {
        const buffered = this.reorderBuffer.get(this.nextExpectedSeq);
        this.reorderBuffer.delete(this.nextExpectedSeq);
        if (this.writable) {
          this.writeQueue.push(buffered);
          this.scheduleWriteDrain();
        } else {
          this.receivedChunks.push(buffered);
        }
        this.nextExpectedSeq++;
      }
    } else {
      this.reorderBuffer.set(seq, chunk);
    }

    // Throttle progress
    const now = performance.now();
    if (now - this.lastProgressTime > PROGRESS_INTERVAL_MS) {
      this.onProgress(this.receivedBytes, this.fileSize);
      this.lastProgressTime = now;
    }

    if (this.fileSize > 0 && this.receivedBytes > this.fileSize) {
      reject(new Error('Received more data than expected'));
    }

    // All chunks received, in order, and complete signal arrived
    if (this.completeReceived && this.reorderBuffer.size === 0) {
      this.onProgress(this.receivedBytes, this.fileSize);
      await this.drainWriteQueue();
      const file = await this.finishDownload();
      resolve(file);
    }
  }

  scheduleWriteDrain() {
    if (this.draining) return;
    this.draining = true;
    queueMicrotask(() => this.drainWriteQueue());
  }

  async drainWriteQueue() {
    this.draining = false;
    while (this.writeQueue.length > 0) {
      const chunk = this.writeQueue.shift();
      await this.writable.write(chunk);
    }
  }

  async finishDownload() {
    if (this.writable) {
      await this.writable.close();
      this.writable = null;
      return this.fileHandle.getFile();
    }

    const blob = new Blob(this.receivedChunks, { type: 'video/*' });
    const file = new File([blob], this.fileName || 'movsync-video', { type: blob.type });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = file.name;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
    return file;
  }

  async cleanup() {
    this.cleanups.forEach((cleanup) => cleanup());
    this.cleanups = [];
    if (this.writable) {
      await this.writable.close().catch(() => {});
      this.writable = null;
    }
    if (this.channel) {
      this.channel.close();
      this.channel = null;
    }
    if (this.pc) {
      this.pc.close();
      this.pc = null;
    }
    await clearSignaling(this.roomCode, this.peerId).catch(() => {});
  }
}

export function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value >= 10 || unit === 0 ? 0 : 1)} ${units[unit]}`;
}
