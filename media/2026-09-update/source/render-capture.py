"""Render reviewed native screenshot stories; use --help for manifest/CLI details."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import importlib.util

_scroll_module = importlib.util.spec_from_file_location("native_scroll", Path(__file__).with_name('native-scroll.py'))
_scroll = importlib.util.module_from_spec(_scroll_module)
_scroll_module.loader.exec_module(_scroll)

import cv2
import numpy as np

W, H, FPS = 1920, 1200, 60
DEFAULT_OUT = Path(__file__).resolve().parent
# Seconds of stationary reading BEFORE the pointer approach (or after the pan).
HOLDS = {'navigation': .2, 'reading': .45, 'modal': .55, 'result': 1.4}


def number(value, label):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(f'{label}: expected a finite number')
    return float(value)


def vector(value, length, label):
    if not isinstance(value, list) or len(value) != length:
        raise ValueError(f'{label}: expected {length} numbers')
    return np.array([number(v, label) for v in value], float)


def ease(t):
    t = np.clip(t, 0, 1)
    return t*t*t*(t*(6*t-15)+10)


def prepare(manifest, base, load_sources=True):
    if not isinstance(manifest, dict):
        raise ValueError('Manifest must be an object')
    name = manifest.get('name')
    if not isinstance(name, str) or not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', name):
        raise ValueError('name must be a lowercase hyphenated asset basename')
    entries = manifest.get('shots')
    if not isinstance(entries, list) or not entries:
        raise ValueError('shots must be a nonempty array')
    poster = manifest.get('poster_shot', len(entries)-1)
    if type(poster) is not int or not 0 <= poster < len(entries):
        raise ValueError('poster_shot must be a zero-based shot index')
    shots, images = [], {}
    previous_box = None
    pointer = np.array([W*.45, H*.55])
    offset = 0
    for i, item in enumerate(entries):
        label = f'shot {i}'
        if not isinstance(item, dict) or not {'source', 'seconds', 'crop', 'pan', 'target'} <= item.keys():
            raise ValueError(f'{label}: source, seconds, crop, pan and target are required')
        if not isinstance(item['source'], str) or not item['source']:
            raise ValueError(f'{label}: source must be a file path')
        source = (base / item['source']).resolve()
        seconds, pan = number(item['seconds'], label), number(item['pan'], label)
        frames = round(seconds*FPS)
        if seconds < .9 or abs(seconds*FPS-frames) > 1e-6:
            raise ValueError(f'{label}: seconds must be >= 0.9 and an exact number of 60fps frames')
        if pan < 0 or pan >= seconds:
            raise ValueError(f'{label}: pan must be nonnegative and shorter than the shot')
        box = vector(item['crop'], 4, label+' crop')
        x, y, w, h = box
        if w <= 0 or h <= 0 or x < 0 or y < 0 or x+w > W+1e-6 or y+h > H+1e-6 or abs(w/h-W/H) > 1e-6:
            raise ValueError(f'{label}: crop [x,y,width,height] must fit 1920x1200 and have aspect ratio 8:5')
        if w < 960:
            raise ValueError(f'{label}: zoom beyond 2x would discard too much context')
        kind = item.get('kind', 'result' if i == len(entries)-1 else 'reading')
        if kind not in HOLDS:
            raise ValueError(f'{label}: kind must be one of {list(HOLDS)}')
        cut = item.get('cut', False)
        if type(cut) is not bool:
            raise ValueError(f'{label}: cut must be boolean')
        start_box = (box.copy() if cut or (previous_box is None and pan == 0)
                     else np.array([0,0,W,H],float) if previous_box is None
                     else previous_box.copy())
        motion = item.get('camera_move')
        if motion is None and not np.allclose(start_box, box) and pan < .35:
            raise ValueError(f'{label}: camera movement needs pan >= 0.35; use cut:true for an intentional editorial cut')
        context = item.get('context')
        if motion is not None:
            if not isinstance(motion, dict) or set(motion) != {'start', 'seconds'}:
                raise ValueError(f'{label}: camera_move requires start and seconds')
            if (i == 0 or cut or pan != 0 or item['target'] is None
                    or not isinstance(context, str) or not context.strip()
                    or entries[i-1].get('context') != context
                    or entries[i-1]['target'] is None):
                raise ValueError(f'{label}: coordinated movement needs consecutive related-control shots with the same explicit context, previous/current targets, pan:0 and no cut')
            motion = {key: number(value, label+' camera_move') for key,value in motion.items()}
            if motion['start'] < HOLDS[kind] or motion['seconds'] < .35:
                raise ValueError(f'{label}: coordinated movement needs a readable starting hold and >=0.35s travel')
            if np.allclose(start_box, box):
                raise ValueError(f'{label}: omit camera_move when the camera does not change')
        scroll_spec = item.get('scroll_transition')
        if scroll_spec is not None:
            if not isinstance(scroll_spec, dict) or set(scroll_spec) != {'viewport','scrollbar','direction','start','seconds'}:
                raise ValueError(f'{label}: scroll_transition requires viewport, scrollbar, direction, start, seconds')
            scroll_spec = dict(scroll_spec)
            viewport = _scroll.rectangle(scroll_spec['viewport'], label+' viewport')
            rail = _scroll.rectangle(scroll_spec['scrollbar'], label+' scrollbar')
            if not (viewport[0] < rail[0] < rail[2] <= viewport[2] and viewport[1] <= rail[1] < rail[3] <= viewport[3]):
                raise ValueError(f'{label}: scrollbar must be inside right edge of viewport')
            if scroll_spec['direction'] not in ('up','down'):
                raise ValueError(f'{label}: scroll direction must be up or down')
            for key in ('start','seconds'):
                scroll_spec[key] = number(scroll_spec[key],label+' scroll '+key)
                if abs(scroll_spec[key]*FPS-round(scroll_spec[key]*FPS)) > 1e-6:
                    raise ValueError(f'{label}: scroll timing must use exact 60fps frames')
            if (i == 0 or cut or pan != 0 or motion is not None or not np.allclose(start_box,box)
                    or scroll_spec['start'] < 0 or scroll_spec['seconds'] < .35):
                raise ValueError(f'{label}: scroll requires a previous shot, fixed crop, no cut/camera movement and >=0.35s travel')
            scroll_end = scroll_spec['start']+scroll_spec['seconds']
            if seconds-scroll_end < HOLDS[kind]:
                raise ValueError(f'{label}: scroll needs a full stationary {kind} hold after arrival')
        cursor_source = None
        if 'cursor_start' in item:
            if motion is not None:
                raise ValueError(f'{label}: cursor_start cannot reset an intentional coordinated move')
            cursor_source = vector(item['cursor_start'], 2, label+' cursor_start')
            if np.any(cursor_source < [0,0]) or np.any(cursor_source > [W,H]):
                raise ValueError(f'{label}: cursor_start must lie in the source screenshot')
            pointer = (cursor_source-start_box[:2])*[W,H]/start_box[2:]
        target = item['target']
        click = frames-13 if target is not None else None
        destination = pointer.copy()
        move_start, move_duration = 0., .6
        if target is not None:
            point = vector(target, 2, label+' target')
            destination = (point-box[:2])*[W,H]/box[2:]
            if np.any(destination < [30,30]) or np.any(destination > [W-45,H-45]):
                raise ValueError(f'{label}: target/cursor falls outside the settled crop')
            if motion is not None:
                move_start, move_duration = motion['start'], motion['seconds']
                if move_start+move_duration > click/FPS-.3+1e-9:
                    raise ValueError(f'{label}: camera/cursor must arrive at least 0.3s before clicking')
            else:
                move_end = (click-12)/FPS
                move_duration = min(1.15, move_end-pan-HOLDS[kind])
                if move_duration < .3-1e-9:
                    raise ValueError(f'{label}: lengthen seconds to allow pan, {HOLDS[kind]}s {kind} hold, >=0.3s cursor move and click feedback')
                move_start = move_end-move_duration
        elif seconds-pan < HOLDS[kind]:
            raise ValueError(f'{label}: lengthen the stationary {kind} hold to {HOLDS[kind]}s')
        if i == len(entries)-1 and target is not None and not manifest.get("segment", False):
            raise ValueError('Final shot must have target:null; include the completed result after the final click')
        if scroll_spec is not None and target is not None and move_start < scroll_end+HOLDS[kind]-1e-9:
            raise ValueError(f'{label}: cursor approach must follow scroll and stationary hold')
        image = None
        if load_sources:
            if source not in images:
                image = cv2.imread(str(source))
                if image is None or image.shape != (H,W,3):
                    raise ValueError(f'{label}: source must be a readable 1920x1200 screenshot: {source}')
                images[source] = image
            image = images[source]
        scroll = (_scroll.compile_scroll(shots[-1]['image'],image,scroll_spec)
                  if scroll_spec is not None and load_sources else None)
        shots.append(dict(scroll_transition=scroll_spec, scroll=scroll, source=source, image=image, frames=frames, offset=offset,
                          box=box, start_box=start_box, pan=pan, click=click, kind=kind,
                          start_pointer=pointer.copy(), destination=destination,
                          move_start=move_start, move_duration=move_duration,
                          camera_move=motion, context=context, cursor_source=cursor_source,
                          target_point=np.array(target,float) if target is not None else None))
        # Check every output-frame position without reading or rendering pixels.
        for f in range(frames):
            current_box, current_pointer = positions(shots[-1],f)
            if target is not None and (np.any(current_pointer < [30,30]) or np.any(current_pointer > [W-45,H-45])):
                raise ValueError(f'{label}: cursor leaves the view during movement at frame {f}')
            if click is not None and f >= click:
                if not np.allclose(current_box,box) or not np.allclose(current_pointer,destination,atol=.001):
                    raise ValueError(f'{label}: click occurs before camera/cursor arrival')
        previous_box, pointer = box, destination
        offset += frames
    return shots, poster


def view(image, box):
    x,y,w,h = box
    x,y = np.clip(x,0,W-w), np.clip(y,0,H-h)
    matrix = np.array([[w/W,0,x],[0,h/H,y]],np.float32)
    return cv2.warpAffine(image,matrix,(W,H),flags=cv2.INTER_CUBIC|cv2.WARP_INVERSE_MAP)


def cursor(frame, point, age=None):
    if age is not None and 0 <= age <= .2:
        progress = age/.2
        layer = frame.copy()
        cv2.circle(layer,tuple(np.rint(point).astype(int)),round(10+23*ease(progress)),(255,190,100),2,cv2.LINE_AA)
        cv2.addWeighted(layer,1-progress,frame,progress,0,dst=frame)
    scale = 1-.1*np.sin(np.pi*age/.15) if age is not None and 0 <= age < .15 else 1
    points = np.array([[0,0],[0,28],[8,22],[15,35],[20,32],[13,20],[24,20]],float)
    points = np.rint(points*scale+point).astype(np.int32)
    cv2.fillPoly(frame,[points],(12,14,18),lineType=cv2.LINE_AA)
    cv2.polylines(frame,[points],True,(247,247,247),2,cv2.LINE_AA)


def positions(shot, f):
    t = f/FPS
    progress = ease((t-shot['move_start'])/shot['move_duration'])
    if shot['camera_move'] is not None:
        # Explicit source-space travel between related controls, not mouse tracking.
        box = shot['start_box']+(shot['box']-shot['start_box'])*progress
        source_start = shot['start_box'][:2]+shot['start_pointer']*shot['start_box'][2:]/[W,H]
        point = source_start+(shot['target_point']-source_start)*progress
        pointer = (point-box[:2])*[W,H]/box[2:]
    else:
        box = shot['start_box']+(shot['box']-shot['start_box'])*ease(t/shot['pan']) if shot['pan'] else shot['box']
        # A manually specified captured cursor position stays over the same UI
        # region through camera movement; screenshots and native hover remain intact.
        start_pointer = ((shot['cursor_source']-box[:2])*[W,H]/box[2:]
                         if shot['cursor_source'] is not None else shot['start_pointer'])
        pointer = start_pointer+(shot['destination']-start_pointer)*progress
    return box, pointer


def frame_at(shot, f):
    box, pointer = positions(shot,f)
    source = shot['image']
    if shot['scroll_transition'] is not None:
        if shot['scroll'] is None:
            raise ValueError('Scroll pixels have not been validated; load sources first')
        spec = shot['scroll_transition']
        source = _scroll.composite_scroll(shot['scroll'],ease((f/FPS-spec['start'])/spec['seconds']))
    frame = view(source,box)
    if shot['click'] is not None:
        age = (f-shot['click'])/FPS if f >= shot['click'] else None
        if age is not None and (not np.allclose(pointer,shot['destination'],atol=.001) or not np.allclose(box,shot['box'])):
            raise RuntimeError('Click and settled camera are misaligned')
        cursor(frame,pointer,age)
    return frame


def audit(shots):
    return {'fps':FPS, 'dimensions':[W,H], 'frames':sum(s['frames'] for s in shots),
            'seconds':sum(s['frames'] for s in shots)/FPS,
            'shots':[{'source':str(s['source']), 'seconds':s['frames']/FPS,
                      'start_frame':s['offset'], 'click_frame':s['offset']+s['click'] if s['click'] is not None else None,
                      'scroll_transition':s['scroll_transition'], 'scroll_validation':s['scroll']['report'] if s['scroll'] is not None else None, 'pan':s['pan'], 'kind':s['kind'], 'camera_move':s['camera_move'], 'context':s['context'], 'cursor_start':s['cursor_source'].tolist() if s['cursor_source'] is not None else None} for s in shots]}


def render(shots, poster, name, output, versions):
    for executable in ['ffmpeg','ffprobe']:
        if not shutil.which(executable):
            raise ValueError(f'{executable} is required')
    if versions is not None:
        data = json.loads(versions.read_text())
        if not isinstance(data,dict):
            raise ValueError('Version file must contain an object')
    output.mkdir(parents=True,exist_ok=True)
    # Stage and verify before replacing published assets; no unrelated thumbnails.
    with tempfile.TemporaryDirectory(prefix=f'.{name}-render-',dir=output) as scratch:
        temporary = Path(scratch)/f'{name}.mp4'
        poster_tmp = Path(scratch)/f'{name}.jpg'
        command = ['ffmpeg','-hide_banner','-loglevel','error','-y','-f','rawvideo',
                   '-pixel_format','bgr24','-video_size',f'{W}x{H}','-framerate',str(FPS),
                   '-i','-','-an','-c:v','libx264','-preset','medium','-crf','15',
                   '-pix_fmt','yuv420p','-movflags','+faststart','-threads','4',str(temporary)]
        process = subprocess.Popen(command,stdin=subprocess.PIPE)
        try:
            for index, shot in enumerate(shots):
                for f in range(shot['frames']):
                    process.stdin.write(frame_at(shot,f).tobytes())
                print(f'Rendered shot {index+1}/{len(shots)}',flush=True)
        finally:
            process.stdin.close()
            code = process.wait()
        if code:
            raise RuntimeError(f'ffmpeg exited {code}')
        info = json.loads(subprocess.check_output(['ffprobe','-v','error','-count_frames',
            '-select_streams','v:0','-show_entries','stream=codec_name,width,height,avg_frame_rate,nb_read_frames,pix_fmt',
            '-of','json',str(temporary)]))['streams'][0]
        expected = ('h264',W,H,'60/1',sum(s['frames'] for s in shots),'yuv420p')
        actual = (info['codec_name'],info['width'],info['height'],info['avg_frame_rate'],int(info['nb_read_frames']),info['pix_fmt'])
        if actual != expected:
            raise RuntimeError(f'Unexpected video properties: {info}')
        subprocess.run(['ffmpeg','-v','error','-xerror','-i',str(temporary),'-f','null','-'],check=True)
        selected = shots[poster]
        if not cv2.imwrite(str(poster_tmp),view(selected['image'],selected['box']),[cv2.IMWRITE_JPEG_QUALITY,97]):
            raise RuntimeError('Poster encoding failed')
        digest = hashlib.sha256(temporary.read_bytes()+poster_tmp.read_bytes()).hexdigest()[:12]
        temporary.replace(output/f'{name}.mp4')
        poster_tmp.replace(output/f'{name}.jpg')
        if versions is not None:
            data = json.loads(versions.read_text())
            data[name] = digest
            versions.write_text(json.dumps(data,indent=2)+'\n')
        print(json.dumps({'name':name,'hash':digest,**audit(shots)},indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__,epilog='Manifest: {"name":"browse-projects","reviewed_loaded":true,"poster_shot":1,"shots":[{"source":"captures/01.png","seconds":2.0,"crop":[0,0,1920,1200],"pan":0,"target":[900,400],"kind":"reading"},{"source":"captures/02.png","seconds":3.0,"crop":[0,0,1920,1200],"pan":0,"target":null,"kind":"result"}]}. Paths are relative to the manifest. Optional shot kind: navigation/reading/modal/result; optional cut:true resets camera at an editorial cut. For deliberately coordinated travel, use camera_move:{"start":0.8,"seconds":0.8}, pan:0, and the same context string on consecutive related-control shots. The camera and cursor move together from the previous crop/control to this crop/target, then settle >=0.3s before clicking. This is opt-in and never follows other moves. Optional cursor_start:[x,y] anchors an explicit source-space cursor position during ordinary camera framing; use only when matching captured hover/context, not to change the UI. reviewed_loaded:true attests human/visual review of every source for loading indicators, placeholders and broken assets; this is not automatic loading detection.')
    parser.add_argument('manifest',type=Path)
    parser.add_argument('--output-dir',type=Path,default=DEFAULT_OUT)
    parser.add_argument('--versions',type=Path,help='Opt in to updating only the manifest name in this existing JSON hash map')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--plan-only',action='store_true',help='Validate timing/geometry without loading screenshots or writing files')
    mode.add_argument('--check',action='store_true',help='Also check source dimensions; no media writes; visual review still required')
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text())
        shots, poster = prepare(manifest,args.manifest.resolve().parent,not args.plan_only)
        if args.plan_only or args.check:
            print(json.dumps(audit(shots),indent=2))
            return
        if manifest.get('reviewed_loaded') is not True:
            raise ValueError('Review every source for fully loaded UI, then set reviewed_loaded:true; automated geometry checks cannot detect loading states')
        render(shots,poster,manifest['name'],args.output_dir,args.versions)
    except (ValueError,OSError,subprocess.CalledProcessError) as error:
        parser.exit(2,f'Error: {error}\n')


if __name__ == '__main__':
    main()
