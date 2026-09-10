"""Bounded translation of overlapping captured pixels, with captured scrollbars."""
import cv2
import numpy as np


def rectangle(value, label):
    if (not isinstance(value, list) or len(value) != 4
            or any(type(v) is not int for v in value)):
        raise ValueError(f'{label}: expected integer [left,top,right,bottom]')
    x,y,r,b = value
    if not (0 <= x < r <= 1920 and 0 <= y < b <= 1200):
        raise ValueError(f'{label}: rectangle must fit source')
    return x,y,r,b


def compile_scroll(previous, current, spec):
    x,y,r,b = spec['viewport']
    sx,sy,sr,sb = spec['scrollbar']
    # Require stationary surroundings; tiny capture AA differences are tolerated.
    mask = np.ones(previous.shape[:2], bool)
    mask[y:b,x:r] = False
    delta = np.abs(previous.astype(np.int16)-current.astype(np.int16))
    if delta[mask].mean() > .05 or np.mean(np.max(delta[mask],axis=1)>3) > .001:
        raise ValueError('scroll: surroundings/header changed; supply matching clean captures')
    a, z = previous[y:b,x:sx], current[y:b,x:sx]
    h = b-y
    # Match the entire overlap, not a repeated blank row or a single text feature.
    ga = cv2.cvtColor(a,cv2.COLOR_BGR2GRAY).astype(np.float32)[:,::4]
    gz = cv2.cvtColor(z,cv2.COLOR_BGR2GRAY).astype(np.float32)[:,::4]
    down = spec['direction'] == 'down'
    top, bottom = (ga,gz) if down else (gz,ga)
    scores = [(float(np.mean(np.abs(top[d:]-bottom[:h-d]))),d)
              for d in range(1,h-95)]
    scores.sort()
    error, distance = scores[0]
    overlap = h-distance
    valid_integer = error <= .25 and scores[1][0] >= max(.5,error*3)
    # HiDPI captures may downsample a native one-pixel scroll to half a pixel.
    # Verify that specific offset against the complete blurred overlap; never
    # accept unmatched content merely by widening the integer-match threshold.
    valid_half = False
    if not valid_integer:
        ta = cv2.GaussianBlur(top, (0,0), 3)
        tb = cv2.GaussianBlur(bottom, (0,0), 3)
        for offset in (distance-.5, distance+.5):
            rows = int(h-offset)-5
            if rows < 96: continue
            xx, yy = np.meshgrid(np.arange(ta.shape[1],dtype=np.float32), np.arange(rows,dtype=np.float32)+offset)
            aligned = cv2.remap(ta,xx,yy,cv2.INTER_LINEAR)
            residual = float(np.abs(aligned[4:]-tb[4:rows]).mean())
            alternatives = [score for score,d in scores if abs(d-offset)>2]
            if residual < .4 and min(alternatives) > 3 and np.std(top[distance:]) >= 5:
                valid_half = True
                break
    if not (valid_integer or valid_half) or np.std(top[distance:]) < 5:
        raise ValueError('scroll: no unique clean overlap of at least 96px; recapture intermediate state')
    first, second = (a,z) if down else (z,a)
    differences = np.abs(first[distance:].astype(float)-second[:overlap]).mean(axis=(1,2))
    # Join only on the best captured row, away from clipping boundaries.
    seam = distance+16+int(np.argmin(differences[16:-16]))
    atlas = np.concatenate([first[:seam],second[seam-distance:]],axis=0)
    if atlas.shape[0] != h+distance:
        raise ValueError('scroll: incomplete content coverage')
    # Obtain the rail and thumb from real screenshot pixels. A shared clean row
    # is valid only if the whole unoccupied track has the same captured background.
    rails = [im[sy:sb,sx:sr] for im in (previous,current)]
    rows,counts = np.unique(np.concatenate(rails).reshape(-1,(sr-sx)*3),axis=0,return_counts=True)
    background = rows[np.argmax(counts)].reshape(1,sr-sx,3)
    thumbs = []
    for rail in rails:
        residual = np.max(np.abs(rail.astype(int)-background.astype(int)),axis=(1,2))
        active = np.flatnonzero(residual>3)
        if not len(active) or np.any(np.diff(active)>1):
            raise ValueError('scroll: scrollbar is not a single captured thumb on a uniform rail')
        lo,hi = int(active[0]),int(active[-1])+1
        if np.max(residual[:lo],initial=0)>3 or np.max(residual[hi:],initial=0)>3:
            raise ValueError('scroll: scrollbar rail changed')
        thumbs.append((lo,hi,rail[lo:hi].copy()))
    lo,hi,thumb = thumbs[0]
    end_lo,end_hi,_ = thumbs[1]
    if abs((hi-lo)-(end_hi-end_lo)) > (2 if min(lo,end_lo)==0 else 1) or (end_lo-lo)*(1 if down else -1)<=0:
        raise ValueError('scroll: thumb size/direction disagrees with content')
    rail = np.repeat(background,sb-sy,axis=0)
    rail[lo:hi] = thumb
    padding = abs(end_lo-lo)+2
    rail = np.concatenate([np.repeat(background,padding,axis=0),rail,np.repeat(background,padding,axis=0)])
    return dict(atlas=atlas, rail=rail, rail_padding=padding, distance=distance, thumb_shift=end_lo-lo,
                previous=previous, current=current, spec=spec,
                report=dict(shift_px=distance if down else -distance,
                            overlap_px=overlap, overlap_mae=error,
                            thumb_shift_px=end_lo-lo, seam_row=seam))


def composite_scroll(scroll, progress):
    if progress <= 0:
        return scroll['previous']
    if progress >= 1:
        return scroll['current']
    spec = scroll['spec']
    x,y,r,b = spec['viewport']
    sx,sy,sr,sb = spec['scrollbar']
    position = scroll['distance']*(progress if spec['direction']=='down' else 1-progress)
    frame = scroll['previous'].copy()
    matrix = np.array([[1,0,0],[0,1,position]],np.float32)
    frame[y:b,x:sx] = cv2.warpAffine(scroll['atlas'],matrix,(sx-x,b-y),
                                    flags=cv2.INTER_LINEAR|cv2.WARP_INVERSE_MAP)
    matrix[1,2] = scroll['rail_padding']-scroll['thumb_shift']*progress
    frame[sy:sb,sx:sr] = cv2.warpAffine(scroll['rail'],matrix,(sr-sx,sb-sy),
                                      flags=cv2.INTER_LINEAR|cv2.WARP_INVERSE_MAP,
                                      borderMode=cv2.BORDER_REPLICATE)
    return frame
