// Load the libav-default wrapper
importScripts('/libav/libav-default.js');

let libav = null;

async function initLibav() {
    if (libav) return;
    // Initialize LibAV using the default variant to support software decoders (AAC/AC3) in WASM
    libav = await LibAV.LibAV({
        variant: 'default',
        base: '/libav',
        wasmurl: '/libav/libav-6.8.8.0-default.wasm.wasm',
        noworker: true
    });
    console.log('[libavWorker] LibAV WASM ready (default)');
}

// ─── Block Reader Device Management ───────────────────────────────────────────
let activeAudioFile = null;
let activeAudioDevName = null;
let activeSubFile = null;
let activeSubDevName = null;

async function setupBlockDevice(file, purpose = 'audio') {
    if (purpose === 'audio') {
        if (activeAudioDevName) {
            try { await libav.unlinkreadaheadfile(activeAudioDevName); } catch(_) {}
        }
        activeAudioFile = file;
        activeAudioDevName = `dev-aud-${Math.random().toString(36).substring(2)}.mkv`;
        console.log(`[libavWorker] setupBlockDevice (audio): size=${file.size}, name=${activeAudioDevName}`);
        await libav.mkreadaheadfile(activeAudioDevName, file);
        return activeAudioDevName;
    } else {
        if (activeSubDevName) {
            try { await libav.unlinkreadaheadfile(activeSubDevName); } catch(_) {}
        }
        activeSubFile = file;
        activeSubDevName = `dev-sub-${Math.random().toString(36).substring(2)}.mkv`;
        console.log(`[libavWorker] setupBlockDevice (sub): size=${file.size}, name=${activeSubDevName}`);
        await libav.mkreadaheadfile(activeSubDevName, file);
        return activeSubDevName;
    }
}

// ─── Global Audio Decoder State ──────────────────────────────────────────────
let decodeFmtCtx = null;
let decodeStream = null;
let decodeCodecCtx = null;
let decodePkt = null;
let decodeFrame = null;
let decodeTrackId = -1;

// WebCodecs fallback state
let nativeDecoder = null;
let nativeDecodedChunks = [];
let nativeDecoderError = null;
let useWebCodecs = false;
let nativeDecoderConfig = null;

function getAudioDataPCM(audioData) {
    const channels = audioData.numberOfChannels;
    const nbSamples = audioData.numberOfFrames;
    const format = audioData.format; // e.g. 'f32', 'f32-planar', 's16', 's16-planar'
    
    // Attempt to copy directly as f32-planar
    try {
        const pcm = [];
        for (let c = 0; c < channels; c++) {
            const sizeBytes = audioData.allocationSize({ planeIndex: c, format: 'f32-planar' });
            const floatCh = new Float32Array(sizeBytes / 4);
            audioData.copyTo(floatCh, { planeIndex: c, format: 'f32-planar' });
            pcm.push(floatCh);
        }
        return pcm;
    } catch (err) {
        console.warn("[libavWorker] AudioData.copyTo conversion to f32-planar failed, converting manually:", err);
        // Fallback to native format copy and manual conversion
        const pcm = [];
        if (format.endsWith('-planar')) {
            // Planar format
            for (let c = 0; c < channels; c++) {
                const sizeBytes = audioData.allocationSize({ planeIndex: c });
                let floatCh = new Float32Array(nbSamples);
                if (format.startsWith('s16')) {
                    const int16Ch = new Int16Array(sizeBytes / 2);
                    audioData.copyTo(int16Ch, { planeIndex: c });
                    for (let i = 0; i < nbSamples; i++) floatCh[i] = int16Ch[i] / 32768.0;
                } else if (format.startsWith('s32')) {
                    const int32Ch = new Int32Array(sizeBytes / 4);
                    audioData.copyTo(int32Ch, { planeIndex: c });
                    for (let i = 0; i < nbSamples; i++) floatCh[i] = int32Ch[i] / 2147483648.0;
                } else if (format.startsWith('u8')) {
                    const uint8Ch = new Uint8Array(sizeBytes);
                    audioData.copyTo(uint8Ch, { planeIndex: c });
                    for (let i = 0; i < nbSamples; i++) floatCh[i] = (uint8Ch[i] / 128.0) - 1.0;
                } else {
                    const f32Ch = new Float32Array(sizeBytes / 4);
                    audioData.copyTo(f32Ch, { planeIndex: c });
                    floatCh.set(f32Ch);
                }
                pcm.push(floatCh);
            }
        } else {
            // Interleaved format
            const sizeBytes = audioData.allocationSize({ planeIndex: 0 });
            let interleaved;
            if (format.startsWith('s16')) {
                interleaved = new Int16Array(sizeBytes / 2);
            } else if (format.startsWith('s32')) {
                interleaved = new Int32Array(sizeBytes / 4);
            } else if (format.startsWith('u8')) {
                interleaved = new Uint8Array(sizeBytes);
            } else {
                interleaved = new Float32Array(sizeBytes / 4);
            }
            audioData.copyTo(interleaved, { planeIndex: 0 });
            
            for (let c = 0; c < channels; c++) {
                const floatCh = new Float32Array(nbSamples);
                if (interleaved instanceof Int16Array) {
                    for (let i = 0; i < nbSamples; i++) {
                        floatCh[i] = interleaved[i * channels + c] / 32768.0;
                    }
                } else if (interleaved instanceof Int32Array) {
                    for (let i = 0; i < nbSamples; i++) {
                        floatCh[i] = interleaved[i * channels + c] / 2147483648.0;
                    }
                } else if (interleaved instanceof Uint8Array) {
                    for (let i = 0; i < nbSamples; i++) {
                        floatCh[i] = (interleaved[i * channels + c] / 128.0) - 1.0;
                    }
                } else {
                    for (let i = 0; i < nbSamples; i++) {
                        floatCh[i] = interleaved[i * channels + c];
                    }
                }
                pcm.push(floatCh);
            }
        }
        return pcm;
    }
}

async function initAudioDecoder(file, trackId) {
    if (decodeFmtCtx && decodeTrackId === trackId) {
        return; // Already initialized for this track
    }
    
    await cleanupAudioDecoder();
    
    // First pass: open file to read stream info (codec params, etc.)
    const devName = await setupBlockDevice(file, 'audio');
    const [fmtCtx, streams] = await libav.ff_init_demuxer_file(devName);
    
    const stream = streams.find(s => s.index === trackId);
    if (!stream) {
        throw new Error(`No stream with index ${trackId}`);
    }
    
    // Save stream info we need for codec init
    const codecId = stream.codec_id;
    const codecpar = stream.codecpar;
    const streamInfo = { ...stream };
    
    // Read extradata before closing (needed for WebCodecs config)
    const sampleRate = await libav.AVCodecParameters_sample_rate(codecpar);
    let codecChannels = await libav.AVCodecParameters_channels(codecpar);
    if (!codecChannels) {
        try { codecChannels = await libav.AVCodecParameters_ch_layout_nb_channels(codecpar); } catch(_) {}
    }
    let extradata = null;
    const extradataPtr = await libav.AVCodecParameters_extradata(codecpar);
    const extradataSize = await libav.AVCodecParameters_extradata_size(codecpar);
    if (extradataPtr && extradataSize > 0) {
        extradata = new Uint8Array(libav.HEAPU8.slice(extradataPtr, extradataPtr + extradataSize));
    }
    
    // Close the first demuxer — we'll re-open fresh for reading
    await libav.avformat_close_input_js(fmtCtx);
    try { await libav.unlinkreadaheadfile(devName); } catch(_) {}
    
    // Second pass: re-open with fresh readahead for actual packet reading
    const devName2 = await setupBlockDevice(file, 'audio');
    const [fmtCtx2, streams2] = await libav.ff_init_demuxer_file(devName2);
    
    // Set discard flag on other streams to speed up packet reading
    for (const s of streams2) {
        if (s.index === trackId) {
            await libav.AVStream_discard_s(s.ptr, libav.AVDISCARD_NONE);
        } else {
            await libav.AVStream_discard_s(s.ptr, libav.AVDISCARD_ALL);
        }
    }
    
    const stream2 = streams2.find(s => s.index === trackId);
    decodeFmtCtx = fmtCtx2;
    decodeStream = stream2;
    decodeTrackId = trackId;
    
    // Allocate packet for demuxing
    decodePkt = await libav.av_packet_alloc();
    if (!decodePkt) {
        throw new Error("Could not allocate packet for demuxing");
    }

    let codecName = 'unknown';
    try {
        codecName = await libav.avcodec_get_name(codecId);
    } catch (_) {}

    useWebCodecs = false;
    let initWasmError = null;
    try {
        // Use stream2's codecpar (from fresh demuxer) for WASM decoder init
        const [, codecCtx, pkt, frame] = await libav.ff_init_decoder(stream2.codec_id, stream2.codecpar);
        await libav.av_packet_free_js(decodePkt);
        decodePkt = pkt;
        decodeCodecCtx = codecCtx;
        decodeFrame = frame;
    } catch (err) {
        initWasmError = err;
    }

    if (initWasmError) {
        console.warn(`[libavWorker] WASM decoder initialization failed for codec ${codecName}. Trying WebCodecs fallback. Error:`, initWasmError);
        
        const webcodecsCodecMap = {
            'aac': 'mp4a.40.2',
            'ac3': 'ac-3',
            'eac3': 'ec-3',
            'mp3': 'mp3',
            'opus': 'opus',
            'flac': 'flac',
            'vorbis': 'vorbis'
        };
        const targetCodec = webcodecsCodecMap[codecName.toLowerCase()] || codecName.toLowerCase();
        
        // Use saved values from first pass (before closing)
        const config = {
            codec: targetCodec,
            sampleRate: sampleRate || 48000,
            numberOfChannels: codecChannels || 2
        };
        if (extradata) {
            config.description = extradata; // already a Uint8Array copy
        }
        
        let isSupported = false;
        try {
            const support = await AudioDecoder.isConfigSupported(config);
            isSupported = support.supported;
        } catch (e) {
            console.error('[libavWorker] AudioDecoder.isConfigSupported failed:', e);
        }
        
        if (!isSupported) {
            throw new Error(`WASM decoder failed: ${initWasmError.message || initWasmError}. WebCodecs does not support codec ${targetCodec}.`);
        }
        
        nativeDecodedChunks = [];
        nativeDecoderError = null;
        nativeDecoderConfig = config;
        nativeDecoder = new AudioDecoder({
            output: (audioData) => {
                try {
                    const pcm = getAudioDataPCM(audioData);
                    nativeDecodedChunks.push({
                        pcm,
                        frames: audioData.numberOfFrames,
                        sampleRate: audioData.sampleRate,
                        channels: audioData.numberOfChannels,
                        timestamp: audioData.timestamp / 1000000.0
                    });
                    audioData.close();
                } catch (e) {
                    console.error('[libavWorker] WebCodecs output callback ERROR:', e);
                    try { audioData.close(); } catch(_) {}
                }
            },
            error: (err) => {
                console.error('[libavWorker] Native AudioDecoder error:', err);
                nativeDecoderError = err;
            }
        });
        
        nativeDecoder.configure(config);
        useWebCodecs = true;
        console.log(`[libavWorker] WebCodecs AudioDecoder initialized for ${targetCodec}. config:`, JSON.stringify({
            codec: config.codec, sampleRate: config.sampleRate, channels: config.numberOfChannels,
            descriptionBytes: config.description ? config.description.length : 0,
            descriptionHex: config.description ? Array.from(config.description.slice(0, 8)).map(b => b.toString(16).padStart(2, '0')).join(' ') : 'none'
        }));
    }
    console.log('[libavWorker] initAudioDecoder complete. useWebCodecs=', useWebCodecs, 
                'nativeDecoder=', !!nativeDecoder, 'decodeCodecCtx=', !!decodeCodecCtx);
}

async function cleanupAudioDecoder() {
    if (decodeFmtCtx) {
        if (decodeCodecCtx) {
            try { await libav.ff_free_decoder(decodeCodecCtx, decodePkt, decodeFrame); } catch (_) {}
        } else if (decodePkt) {
            try { await libav.av_packet_free_js(decodePkt); } catch (_) {}
        }
        try { await libav.avformat_close_input(decodeFmtCtx); } catch (_) {}
        decodeFmtCtx = null;
        decodeStream = null;
        decodeCodecCtx = null;
        decodePkt = null;
        decodeFrame = null;
        decodeTrackId = -1;
    }
    if (nativeDecoder) {
        try { nativeDecoder.close(); } catch (_) {}
        nativeDecoder = null;
    }
    nativeDecodedChunks = [];
    nativeDecoderError = null;
    useWebCodecs = false;
    nativeDecoderConfig = null;
}

// ─── Message Router ───────────────────────────────────────────────────────────
self.onmessage = async (e) => {
    await handleMessage(e);
};

async function handleMessage(e) {
    const { type, file, trackId, timestamp, duration } = e.data;
    console.log(`[libavWorker] Worker got message: ${type}`);

    try {
        await initLibav();
    } catch (err) {
        self.postMessage({ type: 'error', error: `LibAV Init failed: ${err.message || err}`, source: 'global' });
        return;
    }

    if (type === 'probe') {
        try {
            console.log('[libavWorker] Probe: starting...');
            const tracks = await probeFile(file);
            console.log(`[libavWorker] Probe done: ${tracks.length} tracks`);
            self.postMessage({ type: 'probed', tracks });
        } catch (err) {
            self.postMessage({ type: 'error', error: `Probe failed: ${err.message || err}`, source: 'probe' });
        }
    } else if (type === 'start_decode') {
        try {
            await initAudioDecoder(file, trackId);
            
            // Convert timestamp in seconds to stream units
            let num = 1, den = 1000;
            if (decodeStream.time_base) {
                if (Array.isArray(decodeStream.time_base)) {
                    num = decodeStream.time_base[0];
                    den = decodeStream.time_base[1];
                } else if (typeof decodeStream.time_base === 'object') {
                    num = decodeStream.time_base.num || 1;
                    den = decodeStream.time_base.den || 1000;
                }
            } else if (decodeStream.time_base_num && decodeStream.time_base_den) {
                num = decodeStream.time_base_num;
                den = decodeStream.time_base_den;
            }
            const ts = Math.round((timestamp * den) / num);
            console.log('[libavWorker] Seeking: timestamp=', timestamp, 'ts=', ts, 'num=', num, 'den=', den, 'trackId=', decodeTrackId);
            
            // Use avformat_seek_file for more robust seeking
            try {
                const seekRet = await libav.avformat_seek_file(decodeFmtCtx, decodeTrackId, 0, ts, ts, 0);
                console.log('[libavWorker] avformat_seek_file returned:', seekRet);
            } catch(seekErr) {
                console.warn('[libavWorker] avformat_seek_file failed, trying av_seek_frame:', seekErr);
                try {
                    await libav.av_seek_frame(decodeFmtCtx, decodeTrackId, ts, 1 /* AVSEEK_FLAG_BACKWARD */);
                } catch(seekErr2) {
                    console.warn('[libavWorker] av_seek_frame also failed, trying seek to 0:', seekErr2);
                    try {
                        await libav.av_seek_frame(decodeFmtCtx, -1, 0, 1);
                    } catch(_) {}
                }
            }
            
            if (decodeCodecCtx) {
                await libav.avcodec_flush_buffers(decodeCodecCtx);
            }
            if (useWebCodecs && nativeDecoder) {
                nativeDecoder.reset();
                nativeDecoder.configure(nativeDecoderConfig);
                nativeDecodedChunks = [];
            }
            
            await decodeAudioChunk(5.0);
        } catch (err) {
            self.postMessage({ type: 'error', error: `Audio decode start failed: ${err.message || err}`, source: 'audio' });
        }
    } else if (type === 'decode_more') {
        try {
            await decodeAudioChunk(duration || 5.0);
        } catch (err) {
            self.postMessage({ type: 'error', error: `Audio decode failed: ${err.message || err}`, source: 'audio' });
        }
    } else if (type === 'extract_sub') {
        try {
            await extractSubtitleTrack(file, trackId);
        } catch (err) {
            self.postMessage({ type: 'error', error: `Subtitle extraction failed: ${err.message || err}`, source: 'sub' });
        }
    }
}

// ─── Metadata Helpers ────────────────────────────────────────────────────────
function readString(ptr) {
    if (!ptr) return "";
    let end = ptr;
    while (libav.HEAPU8[end] !== 0) end++;
    return new TextDecoder().decode(libav.HEAPU8.subarray(ptr, end));
}

function parseMetadata(dictPtr) {
    if (!dictPtr) return {};
    const count = libav.HEAP32[dictPtr >> 2];
    const elemsPtr = libav.HEAP32[(dictPtr + 4) >> 2];
    const meta = {};
    for (let i = 0; i < count; i++) {
        const keyPtr = libav.HEAP32[(elemsPtr + i * 8) >> 2];
        const valPtr = libav.HEAP32[(elemsPtr + i * 8 + 4) >> 2];
        const key = readString(keyPtr);
        const val = readString(valPtr);
        meta[key.toLowerCase()] = val;
    }
    return meta;
}

// ─── Probe: read stream info from the MKV header ─────────────────────────────
async function probeFile(file) {
    const devName = `dev-probe-${Math.random().toString(36).substring(2)}.mkv`;
    await libav.mkreadaheadfile(devName, file);
    let fmtCtx, streams;
    try {
        console.log(`[libavWorker] Probe: calling ff_init_demuxer_file`);
        [fmtCtx, streams] = await libav.ff_init_demuxer_file(devName);
        console.log(`[libavWorker] Probe: ff_init_demuxer_file success, found ${streams.length} streams`);
    } catch (e) {
        try { await libav.unlinkreadaheadfile(devName); } catch(_) {}
        throw new Error('ff_init_demuxer_file failed: ' + e.message);
    }

    const tracks = [];
    for (const stream of streams) {
        const codecType = stream.codec_type;
        const codecId = stream.codec_id;
        const idx = stream.index;
        
        let codecName = 'unknown';
        try {
            codecName = await libav.avcodec_get_name(codecId);
        } catch (_) {}
        
        console.log(`[libavWorker] Stream #${idx}: codec_type=${codecType}, codec_id=${codecId}, codec_name=${codecName}, AVMEDIA_TYPE_AUDIO=${libav.AVMEDIA_TYPE_AUDIO}, AVMEDIA_TYPE_SUBTITLE=${libav.AVMEDIA_TYPE_SUBTITLE}`);

        let lang = 'und';
        let title = '';
        
        const metadataPtr = libav.HEAP32[(stream.ptr + 72) >> 2];
        if (metadataPtr) {
            try {
                const meta = parseMetadata(metadataPtr);
                if (meta.language) lang = meta.language;
                if (meta.title) title = meta.title;
            } catch (err) {
                console.error('[libavWorker] Failed to parse stream metadata:', err);
            }
        }

        if (codecType === libav.AVMEDIA_TYPE_AUDIO) {
            const count = tracks.filter(t => t.type === 'audio').length;
            const displayTitle = title ? `${title} (${codecName})` : `Audio ${count + 1} (${codecName})`;
            tracks.push({
                id: idx,
                type: 'audio',
                lang,
                title: displayTitle
            });
        } else if (codecType === libav.AVMEDIA_TYPE_SUBTITLE) {
            const count = tracks.filter(t => t.type === 'sub').length;
            const displayTitle = title ? `${title} (${codecName})` : `Subtitle ${count + 1} (${codecName})`;
            tracks.push({
                id: idx,
                type: 'sub',
                lang,
                title: displayTitle
            });
        }
    }

    try { await libav.avformat_close_input(fmtCtx); } catch (_) {}
    try { await libav.unlinkreadaheadfile(devName); } catch(_) {}
    return tracks;
}

// ─── Decode audio chunk to PCM ────────────────────────────────────────────────
async function decodeAudioChunk(targetDuration) {
    if (!decodeFmtCtx) return;

    console.log('[libavWorker] decodeAudioChunk called. useWebCodecs=', useWebCodecs,
                'targetDuration=', targetDuration, 'nativeDecoder state=', nativeDecoder?.state);

    let samplesDecoded = 0;
    let firstPts = null;
    let chunks = [];
    let sampleRate = 48000;
    let channels = 2;
    let eof = false;

    // --- Determine time_base once, outside the loop ---
    let num = 1, den = 1000;
    if (decodeStream.time_base) {
        if (Array.isArray(decodeStream.time_base)) {
            num = decodeStream.time_base[0];
            den = decodeStream.time_base[1];
        } else if (typeof decodeStream.time_base === 'object') {
            num = decodeStream.time_base.num || 1;
            den = decodeStream.time_base.den || 1000;
        }
    } else if (decodeStream.time_base_num && decodeStream.time_base_den) {
        num = decodeStream.time_base_num;
        den = decodeStream.time_base_den;
    }

    let readIterations = 0;
    const MAX_READ_ITERATIONS = 500;
    while (!eof && (samplesDecoded / sampleRate) < targetDuration && readIterations < MAX_READ_ITERATIONS) {
        readIterations++;
        const [ret, pkts] = await libav.ff_read_frame_multi(decodeFmtCtx, decodePkt, { limit: 2048 });
        if (ret === libav.AVERROR_EOF) eof = true;

        const myPkts = pkts[decodeTrackId] || [];
        if (readIterations <= 3) {
            console.log('[libavWorker] iter', readIterations, 'pktStreams=', Object.keys(pkts).join(','),
                        'myPkts=', myPkts.length, 'eof=', eof);
        }
        if (myPkts.length === 0) {
            if (eof) break;
            continue;
        }

        if (firstPts === null) {
            const validPkt = myPkts.find(p => p.pts !== undefined && p.pts !== null);
            if (validPkt) firstPts = validPkt.pts;
        }

        if (useWebCodecs) {
            let decodeCount = 0;
            for (const p of myPkts) {
                if (!p.data) continue;
                // CRITICAL: copy packet data — p.data may be a WASM heap view
                // that gets invalidated on the next ff_read_frame_multi call
                const dataCopy = new Uint8Array(p.data.length);
                dataCopy.set(p.data);

                const tsUs = Math.round((p.pts * 1000000 * num) / den);
                const durationUs = p.duration > 0
                    ? Math.round((p.duration * 1000000 * num) / den)
                    : 23220; // ~1024 samples @ 44100 Hz fallback

                nativeDecoder.decode(new EncodedAudioChunk({
                    type: 'key',
                    timestamp: tsUs,
                    duration: durationUs,
                    data: dataCopy
                }));
                decodeCount++;
            }

            // CRITICAL: yield to the event loop so WebCodecs output callbacks can fire
            await new Promise(r => setTimeout(r, 0));

            if (readIterations <= 3) {
                console.log('[libavWorker] WebCodecs: fed', decodeCount, 'chunks, queueSize=', nativeDecoder.decodeQueueSize,
                            'state=', nativeDecoder.state, 'outputSoFar=', nativeDecodedChunks.length,
                            'error=', nativeDecoderError?.message || 'none');
            }

            // Collect whatever has been asynchronously emitted
            for (const decoded of nativeDecodedChunks) {
                sampleRate = decoded.sampleRate;
                channels = decoded.channels;
                samplesDecoded += decoded.frames;
                chunks.push(decoded);
            }
            nativeDecodedChunks = [];

        } else {
            const frames = await libav.ff_decode_multi(decodeCodecCtx, decodePkt, decodeFrame, myPkts, eof);
            for (const f of frames) {
                if (!f || !f.data || f.data.length === 0) continue;

                sampleRate = f.sample_rate || sampleRate;
                const nbChannels = f.channels || (Array.isArray(f.data) ? f.data.length : 1);
                channels = nbChannels;
                samplesDecoded += f.nb_samples;

                let copiedData = [];
                if (Array.isArray(f.data)) {
                    copiedData = f.data.map(ch => {
                        if (ch instanceof Float32Array) return new Float32Array(ch);
                        const floatCh = new Float32Array(ch.length);
                        if (ch instanceof Int16Array) {
                            for (let i = 0; i < ch.length; i++) floatCh[i] = ch[i] / 32768.0;
                        } else if (ch instanceof Int32Array) {
                            for (let i = 0; i < ch.length; i++) floatCh[i] = ch[i] / 2147483648.0;
                        } else if (ch instanceof Uint8Array) {
                            for (let i = 0; i < ch.length; i++) floatCh[i] = (ch[i] / 128.0) - 1.0;
                        } else {
                            for (let i = 0; i < ch.length; i++) floatCh[i] = ch[i];
                        }
                        return floatCh;
                    });
                } else {
                    const interleaved = f.data;
                    const nbSamples = f.nb_samples;
                    for (let c = 0; c < nbChannels; c++) {
                        const floatCh = new Float32Array(nbSamples);
                        if (interleaved instanceof Float32Array) {
                            for (let i = 0; i < nbSamples; i++) floatCh[i] = interleaved[i * nbChannels + c];
                        } else if (interleaved instanceof Int16Array) {
                            for (let i = 0; i < nbSamples; i++) floatCh[i] = interleaved[i * nbChannels + c] / 32768.0;
                        } else if (interleaved instanceof Int32Array) {
                            for (let i = 0; i < nbSamples; i++) floatCh[i] = interleaved[i * nbChannels + c] / 2147483648.0;
                        } else if (interleaved instanceof Uint8Array) {
                            for (let i = 0; i < nbSamples; i++) floatCh[i] = (interleaved[i * nbChannels + c] / 128.0) - 1.0;
                        } else {
                            for (let i = 0; i < nbSamples; i++) floatCh[i] = interleaved[i * nbChannels + c];
                        }
                        copiedData.push(floatCh);
                    }
                }
                chunks.push({ pcm: copiedData, frames: f.nb_samples });
            }
        }
    }

    // ← FIXED: flush ONCE here, after the loop, to drain the decoder
    if (useWebCodecs && nativeDecoder) {
        await nativeDecoder.flush();
        console.log('[libavWorker] flush complete. nativeDecodedChunks.length=', nativeDecodedChunks.length,
                    'samplesDecoded=', samplesDecoded, 'chunks.length=', chunks.length);
        if (nativeDecoderError) throw nativeDecoderError;

        for (const decoded of nativeDecodedChunks) {
            sampleRate = decoded.sampleRate;
            channels = decoded.channels;
            samplesDecoded += decoded.frames;
            chunks.push(decoded);
        }
        nativeDecodedChunks = [];
        console.log('[libavWorker] after post-loop drain. chunks.length=', chunks.length,
                    'samplesDecoded=', samplesDecoded, 'sampleRate=', sampleRate);
    }

    if (chunks.length > 0) {
        const mergedPcm = [];
        for (let c = 0; c < channels; c++) {
            const totalSamples = chunks.reduce((acc, chunk) => acc + (chunk.pcm[c] ? chunk.pcm[c].length : 0), 0);
            const channelData = new Float32Array(totalSamples);
            let offset = 0;
            for (const chunk of chunks) {
                if (chunk.pcm[c]) {
                    channelData.set(chunk.pcm[c], offset);
                    offset += chunk.pcm[c].length;
                }
            }
            mergedPcm.push(channelData);
        }

        // Use WebCodecs timestamp from first decoded chunk (already in seconds)
        // For WASM path, convert firstPts via time_base
        let timestamp = 0;
        if (useWebCodecs && chunks.length > 0 && chunks[0].timestamp !== undefined) {
            timestamp = chunks[0].timestamp; // already seconds from AudioData.timestamp/1e6
        } else if (firstPts !== null) {
            timestamp = (firstPts * num) / den;
        }

        self.postMessage({
            type: 'audio_chunk',
            pcm: mergedPcm,
            channels,
            frames: samplesDecoded,
            sampleRate,
            timestamp
        }, mergedPcm.map(ch => ch.buffer));
    } else if (!eof) {
        // No chunks decoded — tell main thread so it doesn't stay stuck
        self.postMessage({ type: 'audio_chunk_empty' });
    }

    if (eof) {
        self.postMessage({ type: 'audio_done' });
    }
}


// ─── Extract subtitle track ───────────────────────────────────────────────────
function formatAssTime(ms) {
    if (ms < 0) ms = 0;
    const h = Math.floor(ms / 3600000);
    ms %= 3600000;
    const m = Math.floor(ms / 60000);
    ms %= 60000;
    const s = Math.floor(ms / 1000);
    const cs = Math.floor((ms % 1000) / 10);
    
    const pad = (n, width) => String(n).padStart(width, '0');
    return `${h}:${pad(m, 2)}:${pad(s, 2)}.${pad(cs, 2)}`;
}

async function extractSubtitleTrack(file, trackId) {
    const devName = await setupBlockDevice(file, 'sub');
    const [fmtCtx, streams] = await libav.ff_init_demuxer_file(devName);
    
    // Set discard flag on other streams to speed up demuxing subtitles
    for (const s of streams) {
        if (s.index === trackId) {
            await libav.AVStream_discard_s(s.ptr, libav.AVDISCARD_NONE);
        } else {
            await libav.AVStream_discard_s(s.ptr, libav.AVDISCARD_ALL);
        }
    }
    
    const stream = streams.find(s => s.index === trackId);
    if (!stream) throw new Error(`No stream with index ${trackId}`);
    
    const codecId = stream.codec_id;
    let codecName = 'unknown';
    try {
        codecName = await libav.avcodec_get_name(codecId);
    } catch (_) {}
    
    let num = 1, den = 1000;
    if (stream.time_base) {
        if (Array.isArray(stream.time_base)) {
            num = stream.time_base[0];
            den = stream.time_base[1];
        } else if (typeof stream.time_base === 'object') {
            num = stream.time_base.num || 1;
            den = stream.time_base.den || 1000;
        }
    } else if (stream.time_base_num && stream.time_base_den) {
        num = stream.time_base_num;
        den = stream.time_base_den;
    }

    const pkt = await libav.av_packet_alloc();
    if (!pkt) throw new Error('Could not allocate packet for subtitle extraction');

    const isAss = codecName === 'ass' || codecName === 'ssa';
    const isSrt = codecName === 'subrip' || codecName === 'srt';

    // ── Build ASS header ──
    // For ASS/SSA tracks, the codec extradata contains the real header (styles, fonts, resolution).
    // For SRT/other, use a generic fallback header.
    let assHeader = '';

    if (isAss) {
        try {
            const extradataPtr = await libav.AVCodecParameters_extradata(stream.codecpar);
            const extradataSize = await libav.AVCodecParameters_extradata_size(stream.codecpar);
            if (extradataPtr && extradataSize > 0) {
                const extradata = libav.HEAPU8.slice(extradataPtr, extradataPtr + extradataSize);
                let headerText = new TextDecoder().decode(extradata).trim();
                // Extradata typically contains everything up to [Events].
                // Append the [Events] section if not already present.
                if (!headerText.includes('[Events]')) {
                    headerText += '\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n';
                } else if (!headerText.includes('Format: Layer')) {
                    // Has [Events] but missing Format line
                    headerText += '\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n';
                }
                assHeader = headerText + '\n';
                console.log(`[libavWorker] Using ASS header from codec extradata (${extradataSize} bytes)`);
            }
        } catch (err) {
            console.warn('[libavWorker] Failed to read ASS extradata, using fallback header:', err);
        }
    }

    if (!assHeader) {
        // Fallback generic header for SRT or missing ASS extradata
        assHeader = [
            '[Script Info]', 'ScriptType: v4.00+', 'PlayResX: 640', 'PlayResY: 360',
            '[V4+ Styles]',
            'Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding',
            'Style: Default,Arial,24,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1',
            '[Events]', 'Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text', ''
        ].join('\n');
    }

    let dialogues = '';
    let dialogueCount = 0;
    let eof = false;
    let headerSent = false;
    
    try {
        while (!eof) {
            const [ret, pkts] = await libav.ff_read_frame_multi(fmtCtx, pkt, { limit: 256 });
            if (ret === libav.AVERROR_EOF) eof = true;
            for (const p of (pkts[trackId] || [])) {
                if (p.data) {
                    let line = new TextDecoder().decode(p.data).trim();
                    if (line) {
                        if (isSrt) {
                            line = line.replace(/\r?\n/g, '\\N').replace(/\n/g, '\\N');
                            const startMs = Math.round((p.pts * num * 1000) / den);
                            const durationMs = Math.round((p.duration * num * 1000) / den);
                            const endMs = startMs + durationMs;
                            
                            const startStr = formatAssTime(startMs);
                            const endStr = formatAssTime(endMs);
                            dialogues += `Dialogue: 0,${startStr},${endStr},Default,,0,0,0,,${line}\n`;
                        } else if (isAss) {
                            // libav ASS packet data format:
                            //   ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text
                            // ASS Dialogue format requires:
                            //   Dialogue: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
                            // So: drop ReadOrder, keep Layer, INSERT Start/End from PTS, keep rest.
                            const fields = line.split(',');
                            if (fields.length >= 9) {
                                const layer = fields[1]; // Layer
                                const startMs = Math.round((p.pts * num * 1000) / den);
                                const durationMs = Math.round((p.duration * num * 1000) / den);
                                const endMs = startMs + durationMs;
                                const startStr = formatAssTime(startMs);
                                const endStr = formatAssTime(endMs);
                                // fields[2:] = Style,Name,MarginL,MarginR,MarginV,Effect,Text
                                // (Text may contain commas — join preserves them)
                                const restFields = fields.slice(2).join(',');
                                dialogues += `Dialogue: ${layer},${startStr},${endStr},${restFields}\n`;
                            }
                        } else {
                            if (!line.startsWith('Dialogue:')) {
                                line = 'Dialogue: ' + line;
                            }
                            dialogues += line + '\n';
                        }
                    }
                    dialogueCount++;

                    // Send partial results every 100 dialogues so subs appear quickly
                    if (dialogueCount % 100 === 0) {
                        console.log(`[libavWorker] Subtitle progress: ${dialogueCount} dialogues extracted...`);
                        self.postMessage({ type: 'sub_data', text: assHeader + dialogues });
                        headerSent = true;
                    }
                }
            }
        }
    } finally {
        await libav.av_packet_free_js(pkt);
        try { await libav.avformat_close_input(fmtCtx); } catch (_) {}
    }

    // Send final batch
    if (dialogues.length > 0 || !headerSent) {
        console.log(`[libavWorker] Subtitle extraction complete. ${dialogueCount} dialogues total`);
        self.postMessage({ type: 'sub_data', text: assHeader + dialogues });
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────
function sliceFile(file, start, end) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = e => resolve(e.target.result);
        reader.onerror = reject;
        reader.readAsArrayBuffer(file.slice(start, end));
    });
}
