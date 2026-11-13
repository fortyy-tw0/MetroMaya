"""Redraw the cap's gold piping flush against the brown outlines. Usage: python3 hat_piping.py SRC.png DST.png"""
import numpy as np, sys
from PIL import Image
from scipy import ndimage as ndi
def fix(src,dst):
    a=np.asarray(Image.open(src).convert('RGBA')).astype(float)
    rgb=a[...,:3]; al=a[...,3]; H,W=al.shape
    r,g,b=rgb[...,0],rgb[...,1],rgb[...,2]
    op=al>120
    gold=(r>170)&(g>110)&(b<130)&(r-b>70)&op
    lum=rgb.max(2)
    dark=(lum<95)&op
    blue=(b>r+20)&op
    darkness=np.clip((150-lum)/90,0,1)*op
    top=np.where(op)[0].min()
    capzone=np.zeros_like(op); capzone[top:top+int(H*0.36)]=True
    lab,n=ndi.label(ndi.binary_dilation(gold&capzone,iterations=1)&gold)
    out=a.copy(); changed=0
    brown=np.median(rgb[dark&capzone],axis=0)
    my={}
    for i in range(1,n+1):
        ys_,xs_=np.where(lab==i)
        if xs_.max()-xs_.min()+1>=25 and len(ys_)/(xs_.max()-xs_.min()+1)<=4.5: my[i]=ys_.mean()
    ymin=min(my.values()) if my else 0
    rim=[i for i in my if my[i]-ymin<H*0.04]
    for i in rim[1:]: lab[lab==i]=rim[0]
    for i in rim[1:]: my.pop(i)
    for i in range(1,n+1):
        comp=lab==i; ys,xs=np.where(comp)
        if not len(xs): continue
        w=xs.max()-xs.min()+1
        if w<25 or comp.sum()/w>4.5: continue
        cols={x:(ys[xs==x].min(),ys[xs==x].max()) for x in range(xs.min(),xs.max()+1) if (xs==x).any()}
        t=max(2.0,float(np.median([y1-y0+1 for y0,y1 in cols.values()])))
        du=[];dd=[]
        for x,(y0,y1) in cols.items():
            du.append(next((k for k in range(1,9) if y0-k>=0 and dark[y0-k,x]),99))
            dd.append(next((k for k in range(1,9) if y1+k<H and dark[y1+k,x]),99))
        below=np.median(dd)<=np.median(du)
        if rim and i==rim[0]: below=True  # top-rim piping hugs the rim's front edge
        gc=np.median(rgb[comp],axis=0)
        # clear old piping to the nearby navy
        halo=ndi.binary_dilation(comp,iterations=1)&~dark
        for x,(y0,y1) in cols.items():
            ref=y0-3 if below else y1+3
            ref=min(max(ref,0),H-1)
            c=rgb[ref,x] if blue[ref,x] else np.array([40,52,110.])
            for y in range(y0-1,y1+2):
                if 0<=y<H and halo[y,x]: out[y,x,:3]=c
        # subpixel brown edge per column (darkness crossing 0.5)
        def subedge(x,start):
            prev=darkness[start,x]; y=start
            for k in range(1,12):
                yy=start+k if below else start-k
                if not(0<=yy<H): return None
                d=darkness[yy,x]
                if d>=0.5:
                    f=(0.5-prev)/(d-prev+1e-6)
                    yprev=yy-1 if below else yy+1
                    return yprev+(f if below else -f)+ (0.5 if below else 0.5)  # boundary coordinate
                prev=d
            return None
        pts=[]
        for x,(y0,y1) in cols.items():
            e=subedge(x,y0 if below else y1)
            if e is not None: pts.append((x,e))
        if len(pts)<8: continue
        px=np.array([p[0] for p in pts],float); py=np.array([p[1] for p in pts])
        fit=np.polyfit(px,py,2)
        res=py-np.polyval(fit,px); keep=np.abs(res)<1.5
        fit=np.polyfit(px[keep],py[keep],2)
        def ok(x):
            if x<0 or x>=W: return False
            e=np.polyval(fit,x); yy=int(np.floor(e-1.5)) if below else int(np.floor(e+1.5))
            return 0<=yy<H and (blue[yy,x] or gold[yy,x])
        x0,x1=int(px.min()),int(px.max())
        lo,hi=x0-40,x1+40
        while x0-1>lo and ok(x0-1): x0-=1
        while x1+1<hi and ok(x1+1): x1+=1
        for x in range(x0,x1+1):
            e=np.polyval(fit,x)  # boundary between cap surface and brown, in pixel-edge coordinates
            OV=0.9
            g0,g1=(e-t,e+OV) if below else (e-OV,e+t)
            for y in range(int(np.floor(g0))-1,int(np.ceil(g1))+2):
                if not(0<=y<H) or not op[y,x]: continue
                cov=max(0.0,min(y+1,g1)-max(y,g0))
                # brown portion of this pixel (beyond the boundary)
                bcov=max(0.0,min(y+1,e+3)-max(y,e)) if below else max(0.0,min(y+1,e)-max(y,e-3))
                if cov<=0: continue
                base=out[y,x,:3]
                if bcov>0: base=brown*bcov/(1-cov+1e-6)*min(1,(1-cov)) + base*0  if 1-cov>1e-3 else base
                if bcov>0 and 1-cov>1e-3: base=brown
                out[y,x,:3]=gc*cov+base*(1-cov); changed+=1
    Image.fromarray(np.clip(out,0,255).astype(np.uint8)).save(dst,optimize=True)
    return changed
if __name__=='__main__': print(fix(sys.argv[1],sys.argv[2]))
