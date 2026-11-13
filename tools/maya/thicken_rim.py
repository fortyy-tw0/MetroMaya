"""Thicken the cap's top outline so it reads at small sizes. Usage: python3 thicken_rim.py SRC.png DST.png"""
import numpy as np, sys
from PIL import Image
from scipy import ndimage as ndi
OL=np.array([62,36,22.])
def thicken(src,dst,width=2.6):
    a=np.asarray(Image.open(src).convert('RGBA')).astype(float)
    rgb=a[...,:3]; al=a[...,3]; H,W=al.shape
    op=al>110
    ys,xs=np.where(op); top=ys.min()
    blue=(rgb[...,2]>rgb[...,0]+25)&op
    rows=slice(top,top+70)
    bx=np.where(blue[rows].any(0))[0]; x0,x1=bx.min()-3,bx.max()+4
    region=np.zeros_like(op); region[top:top+48,x0:x1]=True
    d=ndi.distance_transform_edt(op)
    w=np.clip((width+0.5-d)/1.0,0,1)*region*op*blue_near(blue)
    out=a.copy(); out[...,:3]=rgb*(1-w[...,None])+OL*w[...,None]
    # also make the soft outer fringe opaque-dark so the rim reads at small sizes
    fringe=region&(al>8)&(al<=110)
    out[fringe,:3]=OL; 
    Image.fromarray(np.clip(out,0,255).astype(np.uint8)).save(dst,optimize=True)
def blue_near(blue):
    return ndi.binary_dilation(blue,iterations=4)
if __name__=='__main__':
    thicken(sys.argv[1],sys.argv[2])
