"""CTA flap strip: maya-hehehe.png -> maya-hehe-fly.webp (wing pivots (136,170) and (252,165), +/-14 deg)."""
from pathlib import Path
IMG = Path(__file__).resolve().parents[2] / 'src/main/resources/static/img'
import numpy as np, math, os
from PIL import Image, ImageDraw
from scipy import ndimage as ndi
full=np.asarray(Image.open(IMG/'maya-hehehe.png').convert('RGBA')).astype(float)
H0,W0=full.shape[:2]; N=16; P=60
pm=full.copy(); pm[...,:3]*=pm[...,3:4]/255
def poly(pts):
    m=Image.new('L',(W0,H0),0); ImageDraw.Draw(m).polygon(pts,fill=255); return np.asarray(m)>0
wingL=poly([(0,0),(126,0),(132,120),(138,175),(105,222),(0,232)])
wingR=poly([(254,0),(344,0),(344,225),(258,222),(254,175),(252,120)])
def weight(m):
    d=ndi.distance_transform_edt(m); t=np.clip((d-2)/45,0,1); return t*t*(3-2*t)
Hc,Wc=H0+2*P,W0+2*P
yy,xx=np.mgrid[0:Hc,0:Wc].astype(float); yy-=P; xx-=P
def samp(a): return ndi.map_coordinates(a,[np.clip(yy,0,H0-1),np.clip(xx,0,W0-1)],order=1)
wL=np.where((xx<0)|((yy<0)&(xx<126)),1.0,samp(weight(wingL))); wR=np.where((xx>=W0)|((yy<0)&(xx>254)),1.0,samp(weight(wingR)))
frames=[]
for k in range(N):
    ph=2*math.pi*k/N; a=math.radians(14*math.sin(ph))
    sx=xx.copy(); sy=yy.copy()
    for ang,wq,(px,py) in [(a,wL,(136,170)),(-a,wR,(252,165))]:
        phi=ang*wq; c,s=np.cos(-phi),np.sin(-phi); dx,dy=sx-px,sy-py
        nsx=px+c*dx-s*dy; nsy=py+s*dx+c*dy; sel=wq>0
        sx=np.where(sel,nsx,sx); sy=np.where(sel,nsy,sy)
    out=np.stack([ndi.map_coordinates(pm[...,ch],[sy,sx],order=1,mode='constant',cval=0) for ch in range(4)],-1)
    al=out[...,3]; rgb=np.where(al[...,None]>0,out[...,:3]*255/np.maximum(al[...,None],1e-6),0)
    frames.append(np.dstack([np.clip(rgb,0,255),np.clip(al,0,255)]).astype(np.uint8))
strip=Image.fromarray(np.concatenate(frames,axis=1),'RGBA'); strip.save(IMG/'maya-hehe-fly.webp',lossless=True,method=6)
print(strip.size, os.path.getsize(IMG/'maya-hehe-fly.webp')//1024,'KB')
for k in range(N):
    a=frames[k][...,3]; ys,xs=np.where(a>10)
    if xs.min()==0 or xs.max()==Wc-1 or ys.min()==0: print('edge',k)
