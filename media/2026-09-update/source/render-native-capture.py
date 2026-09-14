"""Trim a native recording while preserving real typing and UI transitions."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile

import cv2
import numpy as np


def render(manifest_path, output):
    spec = json.loads(manifest_path.read_text())
    source = (manifest_path.parent / spec['source']).resolve()
    info = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'json', str(source)]))
    duration = float(info['format']['duration'])
    segments = spec['segments']
    if not segments:
        raise ValueError('At least one capture segment is required')
    previous_end = 0
    for start, end, speed in segments:
        if not all(math.isfinite(v) for v in (start, end, speed)):
            raise ValueError('Segment values must be finite')
        if not previous_end <= start < end <= duration or not .5 <= speed <= 2:
            raise ValueError('Segments must be chronological and use speeds between 0.5x and 2x')
        previous_end = end
    crop = np.array(spec.get('crop', [0, 0, 1920, 1200]), dtype=float)
    x, y, w, h = crop
    if not (0 <= x <= 1920-w and 0 <= y <= 1200-h and 960 <= w <= 1920
            and h > 0 and abs(w/h-1.6) < 1e-6):
        raise ValueError('Crop must fit the 1920x1200 frame at no more than 2x zoom')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='modtale-native-') as work:
        work = Path(work)
        parts = []
        for i, (start, end, speed) in enumerate(segments):
            part = work / f'{i}.mp4'
            subprocess.run([
                'ffmpeg', '-v', 'error', '-y', '-i', str(source),
                '-vf', f'trim=start={start}:end={end},setpts=(PTS-STARTPTS)/{speed},scale=1920:1200:flags=lanczos,setsar=1,fps=60',
                '-an', '-c:v', 'libx264', '-preset', 'fast', '-crf', '16', '-threads', '2', str(part)], check=True)
            parts.append(part)
        listing = work / 'parts.txt'
        listing.write_text(''.join(f"file '{p}'\n" for p in parts))
        joined = work / 'joined.mp4'
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0',
                        '-i', str(listing), '-c', 'copy', str(joined)], check=True)
        capture = cv2.VideoCapture(str(joined))
        frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
        encoded = work / 'finished.mp4'
        encoder = subprocess.Popen([
            'ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'bgr24', '-s', '1920x1200',
            '-r', '60', '-i', '-', '-an', '-c:v', 'libx264', '-preset', 'fast', '-crf', '16',
            '-pix_fmt', 'yuv420p', '-threads', '2', '-movflags', '+faststart', str(encoded)], stdin=subprocess.PIPE)
        try:
            for i in range(frames):
                ok, frame = capture.read()
                if not ok:
                    raise ValueError(f'Capture decode stopped at frame {i} of {frames}')
                progress = np.clip((i/60-.3)/1.15, 0, 1)
                progress = progress*progress*(3-2*progress)
                x, y, w, h = np.array([0, 0, 1920, 1200]) + progress*(crop-[0, 0, 1920, 1200])
                matrix = np.array([[w/1920, 0, x], [0, h/1200, y]], np.float32)
                frame = cv2.warpAffine(frame, matrix, (1920, 1200), flags=cv2.INTER_CUBIC | cv2.WARP_INVERSE_MAP)
                encoder.stdin.write(frame.tobytes())
                if i == frames-25:
                    cv2.imwrite(str(work/'poster.jpg'), frame, [cv2.IMWRITE_JPEG_QUALITY, 95])
        finally:
            capture.release()
            encoder.stdin.close()
            result = encoder.wait()
        if result:
            raise ValueError(f'Encoder failed with exit code {result}')
        subprocess.run(['ffmpeg', '-v', 'error', '-xerror', '-threads', '2', '-i', str(encoded),
                        '-f', 'null', '-'], check=True)
        encoded.replace(output)
        (work/'poster.jpg').replace(output.with_suffix('.jpg'))
    print(json.dumps({'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                      'frames': frames, 'seconds': frames/60, 'segments': segments, 'crop': crop.tolist()}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    render(args.manifest, args.output)
