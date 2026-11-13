"""Hero flap strip: maya-ta-da.png -> maya-fly-frames.webp (16 frames, body still, wings bend about the shoulders)."""
from pathlib import Path
IMG = Path(__file__).resolve().parents[2] / 'src/main/resources/static/img'
import numpy as np, math
from PIL import Image, ImageDraw
from scipy import ndimage as ndi
full=np.asarray(Image.open(IMG/'maya-ta-da.png').convert('RGBA')).astype(float)
H0,W=full.shape[:2]; CUT=206; N=16; A=9.0; PX=80; PT=30
# premultiplied source
pm=full.copy(); pm[...,:3]*=pm[...,3:4]/255
# body + head region that must stay exactly as drawn
body_poly=[(170,0),(296,0),(299,70),(306,88),(313,105),(314,140),(306,152),(300,165),(298,206),
           (306,375),(128,375),(128,212),(205,206),(196,190),(182,172),(168,152),(160,135),(160,100),(165,88),(168,70)]
m=Image.new('L',(W,H0),0); ImageDraw.Draw(m).polygon(body_poly,fill=255); body=np.asarray(m)>0
d=ndi.distance_transform_edt(~body)
t=np.clip((d-3)/55,0,1); w=t*t*(3-2*t)            # 0 on the body, easing up to 1 out at the wing tips
yy,xx=np.mgrid[0:CUT+PT,0:W+2*PX].astype(float)
yy-=PT; xx-=PX
wq=ndi.map_coordinates(w,[np.clip(yy,0,H0-1),np.clip(xx,0,W-1)],order=1)
# outside the drawing the weight keeps rising, so the far wing tips still move
wq=np.where((xx<0)|(xx>=W),1.0,wq)
left=xx<235
px=np.where(left,180.0,305.0); py=np.where(left,170.0,160.0); sgn=np.where(left,1.0,-1.0)
frames=[]
for k in range(N):
    a=A*math.sin(2*math.pi*k/N)
    phi=np.radians(a)*sgn*wq
    c,s=np.cos(-phi),np.sin(-phi)
    dx,dy=xx-px,yy-py
    sx=px+c*dx-s*dy; sy=py+s*dx+c*dy
    out=np.zeros((CUT+PT,W+2*PX,4))
    for ch in range(4): out[...,ch]=ndi.map_coordinates(pm[...,ch],[sy,sx],order=1,mode='constant',cval=0)
    al=out[...,3]; rgb=np.where(al[...,None]>0,out[...,:3]*255/np.maximum(al[...,None],1e-6),0)
    frames.append(np.dstack([np.clip(rgb,0,255),np.clip(al,0,255)]).astype(np.uint8))
strip=np.concatenate(frames,axis=1)
im=Image.fromarray(strip,'RGBA')
out=IMG/'maya-fly-frames.webp'
im.save(out,lossless=True,quality=100,method=6)
import os; print(out.name, im.size, os.path.getsize(out)//1024, 'KB')
