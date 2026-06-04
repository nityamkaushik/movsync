// Load the full wasm.js bundle directly
importScripts('/libav/libav-6.8.8.0-default.wasm.js');

let libav = null;

async function initLibav() {
    if (libav) return;
    // LibAVFactory returns a Promise<Module>
    libav = await LibAVFactory({
        wasmurl: '/libav/libav-6.8.8.0-default.wasm.wasm'
    });
    self.postMessage({ type: 'error', error: 'LibAV WASM ready' });
}

// ─── Message Router ───────────────────────────────────────────────────────────
self.onmessage = async (e) => {
    const { type, file, trackId } = e.data;
    self.postMessage({ type: 'error', error: `Worker got: ${type} | file: ${file ? file.name : 'NULL'}` });

    try {
        await initLibav();

        if (type === 'probe') {
            self.postMessage({ type: 'error', error: 'Probe: starting...' });
            const tracks = await probeFile(file);
            self.postMessage({ type: 'error', error: `Probe done: ${tracks.length} tracks` });
            self.postMessage({ type: 'probed', tracks });
        } else if (type === 'decode') {
            await decodeAudioTrack(file, trackId);
        } else if (type === 'extract_sub') {
            await extractSubtitleTrack(file, trackId);
        }
    } catch (err) {
        self.postMessage({ type: 'error', error: (err && err.message) ? err.message : String(err) });
    }
};

// ─── Probe: read stream info from the MKV header ─────────────────────────────
async function probeFile(file) {
    // Read first 8MB (enough for MKV headers)
    const chunkSize = Math.min(file.size, 8 * 1024 * 1024);
    const buf = await sliceFile(file, 0, chunkSize);
    self.postMessage({ type: 'error', error: `Probe: read ${buf.byteLength} bytes` });

    libav.writeFile('probe.mkv', new Uint8Array(buf));
    self.postMessage({ type: 'error', error: 'Probe: file written to VFS' });

    let fmtCtx, streams;
    try {
        [fmtCtx, streams] = await libav.ff_init_demuxer_file('probe.mkv');
        self.postMessage({ type: 'error', error: `Probe: found ${streams.length} streams` });
    } catch (e) {
        throw new Error('ff_init_demuxer_file failed: ' + e.message);
    }

    const tracks = [];
    for (const stream of streams) {
        const codecType = stream.codec_type;
        const lang = (stream.tags && stream.tags.language) ? stream.tags.language : 'und';
        const title = (stream.tags && stream.tags.title) ? stream.tags.title : '';
        const idx = stream.index;

        if (codecType === 1 /* AVMEDIA_TYPE_AUDIO */) {
            const count = tracks.filter(t => t.type === 'audio').length;
            tracks.push({
                id: idx,
                type: 'audio',
                lang,
                title: title || `Audio ${count + 1}${lang !== 'und' ? ` (${lang})` : ''}`
            });
        } else if (codecType === 3 /* AVMEDIA_TYPE_SUBTITLE */) {
            const count = tracks.filter(t => t.type === 'sub').length;
            tracks.push({
                id: idx,
                type: 'sub',
                lang,
                title: title || `Subtitle ${count + 1}${lang !== 'und' ? ` (${lang})` : ''}`
            });
        }
    }

    try { await libav.avformat_close_input(fmtCtx); } catch (_) {}
    try { libav.unlink('probe.mkv'); } catch (_) {}
    return tracks;
}

// ─── Decode audio track to PCM ────────────────────────────────────────────────
async function decodeAudioTrack(file, trackId) {
    const buf = await sliceFile(file, 0, file.size);
    libav.writeFile('audio.mkv', new Uint8Array(buf));

    const [fmtCtx, streams] = await libav.ff_init_demuxer_file('audio.mkv');
    const stream = streams.find(s => s.index === trackId);
    if (!stream) throw new Error(`No stream with index ${trackId}`);

    const [, codecCtx, pkt, frame] = await libav.ff_init_decoder(stream.codec_id, stream.codecpar);

    let eof = false;
    while (!eof) {
        const [ret, pkts] = await libav.ff_read_frame_multi(fmtCtx, pkt, { limit: 256 });
        if (ret === libav.AVERROR_EOF) eof = true;

        const myPkts = pkts[trackId] || [];
        if (myPkts.length === 0) continue;

        const frames = await libav.ff_decode_multi(codecCtx, pkt, frame, myPkts, eof);
        for (const f of frames) {
            if (!f || !f.data || f.data.length === 0) continue;
            self.postMessage({
                type: 'audio_chunk',
                pcm: f.data,
                channels: f.channels || f.data.length,
                frames: f.nb_samples,
                sampleRate: f.sample_rate || 48000
            }, f.data.map(ch => ch.buffer));
        }
    }

    try { await libav.ff_free_decoder(codecCtx, pkt, frame); } catch (_) {}
    try { await libav.avformat_close_input(fmtCtx); } catch (_) {}
    try { libav.unlink('audio.mkv'); } catch (_) {}
    self.postMessage({ type: 'audio_done' });
}

// ─── Extract subtitle track ───────────────────────────────────────────────────
async function extractSubtitleTrack(file, trackId) {
    const buf = await sliceFile(file, 0, file.size);
    libav.writeFile('sub.mkv', new Uint8Array(buf));

    const [fmtCtx, streams] = await libav.ff_init_demuxer_file('sub.mkv');
    const stream = streams.find(s => s.index === trackId);
    if (!stream) throw new Error(`No stream with index ${trackId}`);

    const assHeader = [
        '[Script Info]', 'ScriptType: v4.00+', 'PlayResX: 640', 'PlayResY: 360',
        '[V4+ Styles]',
        'Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding',
        'Style: Default,Arial,24,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1',
        '[Events]', 'Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text', ''
    ].join('\n');

    let dialogues = '';
    let eof = false;
    while (!eof) {
        const [ret, pkts] = await libav.ff_read_frame_multi(fmtCtx, null, { limit: 256 });
        if (ret === libav.AVERROR_EOF) eof = true;
        for (const p of (pkts[trackId] || [])) {
            if (p.data) {
                const line = new TextDecoder().decode(p.data).trim();
                if (line) dialogues += 'Dialogue: ' + line + '\n';
            }
        }
    }

    try { await libav.avformat_close_input(fmtCtx); } catch (_) {}
    try { libav.unlink('sub.mkv'); } catch (_) {}
    self.postMessage({ type: 'sub_data', text: assHeader + dialogues });
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
