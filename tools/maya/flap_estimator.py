"""Estimator flap strip (pointing pose). Usage: python3 flap_estimator.py SRC.png DST.webp
Builds maya-hey-fly.webp from maya-hey-you.png and maya-hey-fly-wink.webp from src/maya-hey-you-wink.png;
both go through this same pipeline so the wink layer lines up pixel for pixel."""
import numpy as np, math
from PIL import Image, ImageDraw
from scipy import ndimage as ndi
import sys
SRC=sys.argv[1]; DST=sys.argv[2]
full=np.asarray(Image.open(SRC).convert('RGBA')).astype(float)
H0,W0=full.shape[:2]; N=16; P=60
pm=full.copy(); pm[...,:3]*=pm[...,3:4]/255
def poly(pts):
    m=Image.new('L',(W0,H0),0); ImageDraw.Draw(m).polygon(pts,fill=255); return np.asarray(m)>0
wingR=poly([(250,0),(383,0),(383,262),(252,252),(244,200),(240,176)])
wingL=poly([(0,248),(96,256),(112,296),(96,345),(0,345)])
bodyR=~wingR; bodyL=~wingL
def weight(body):
    d=ndi.distance_transform_edt(~body); t=np.clip((d-2)/45,0,1); return t*t*(3-2*t)
wR=weight(bodyR); wL=weight(bodyL)
Hc,Wc=H0+2*P,W0+2*P
yy,xx=np.mgrid[0:Hc,0:Wc].astype(float); yy-=P; xx-=P
def samp(a): return ndi.map_coordinates(a,[np.clip(yy,0,H0-1),np.clip(xx,0,W0-1)],order=1)
wRq=np.where((xx>=W0)|((yy<0)&(xx>250)),1.0,samp(wR)); wLq=np.where((xx<0)|((yy>=H0)&(xx<100)),1.0,samp(wL))
frames=[]
for k in range(N):
    ph=2*math.pi*k/N
    aR=math.radians(11*math.sin(ph)); aL=math.radians(-6*math.sin(ph))
    sx=xx.copy(); sy=yy.copy()
    for a,wq,(px,py) in [(aR,wRq,(244,182)),(aL,wLq,(100,262))]:
        phi=a*wq; c,s=np.cos(-phi),np.sin(-phi); dx,dy=sx-px,sy-py
        nsx=px+c*dx-s*dy; nsy=py+s*dx+c*dy
        sel=wq>0; sx=np.where(sel,nsx,sx); sy=np.where(sel,nsy,sy)
    out=np.stack([ndi.map_coordinates(pm[...,ch],[sy,sx],order=1,mode='constant',cval=0) for ch in range(4)],-1)
    al=out[...,3]; rgb=np.where(al[...,None]>0,out[...,:3]*255/np.maximum(al[...,None],1e-6),0)
    frames.append(np.dstack([np.clip(rgb,0,255),np.clip(al,0,255)]).astype(np.uint8))
strip=Image.fromarray(np.concatenate(frames,axis=1),'RGBA')
strip.save(DST,lossless=True,method=6)
import os; print(strip.size, os.path.getsize(DST)//1024,'KB')
for k in range(N):
    a=frames[k][...,3]; ys,xs=np.where(a>10)
    if xs.min()==0 or xs.max()==Wc-1 or ys.min()==0 or ys.max()==Hc-1: print('edge touch frame',k)
