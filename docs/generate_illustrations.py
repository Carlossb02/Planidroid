from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path
P=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable-nodpi'
P.mkdir(parents=True, exist_ok=True)
# Original, transparent, supersampled furniture illustrations. Draw coordinates in 256px logical units.
S=3
for name in ['fridge','freezer','wardrobe','drawer','pantry','shelf','cabinet','box','washer','other']:
    im=Image.new('RGBA',(256*S,256*S)); sh=Image.new('RGBA',im.size); sd=ImageDraw.Draw(sh)
    sd.ellipse(tuple(v*S for v in (42,212,220,240)),fill=(20,39,63,55)); im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(7*S)))
    d=ImageDraw.Draw(im)
    def rr(x,y,w,h,r,fill,outline=None,sw=2):d.rounded_rectangle((x*S,y*S,(x+w)*S,(y+h)*S),r*S,fill=fill,outline=outline,width=sw*S)
    def poly(pts,fill):d.polygon([(x*S,y*S) for x,y in pts],fill=fill)
    def line(pts,fill,w=3):d.line([(x*S,y*S) for x,y in pts],fill=fill,width=w*S)
    def ellipse(x,y,w,h,fill,outline=None,sw=2):d.ellipse((x*S,y*S,(x+w)*S,(y+h)*S),fill=fill,outline=outline,width=sw*S)
    def face(x,y,w,h,c1,c2,r=12):
        mask=Image.new('L',im.size); md=ImageDraw.Draw(mask);md.rounded_rectangle((x*S,y*S,(x+w)*S,(y+h)*S),r*S,fill=255)
        grad=Image.new('RGBA',im.size);gd=ImageDraw.Draw(grad)
        for j in range(h*S+1):
            t=j/(h*S);c=tuple(round(a+(b-a)*t) for a,b in zip(c1,c2))
            gd.line((x*S,y*S+j,(x+w)*S,y*S+j),fill=c,width=1)
        im.alpha_composite(Image.composite(grad,Image.new('RGBA',im.size),mask));rr(x,y,w,h,r,None,(64,84,106,140),2)
    blue=(225,243,250,255); steel=(144,184,207,255); edge=(91,127,150,255); white=(249,253,255,255); handle=(61,100,130,255)
    wood=(244,207,161,255); wood2=(199,143,98,255); woodedge=(143,91,55,255)
    def body(x,y,w,h,metal=False):
        c1,c2,side=(blue,steel,edge) if metal else (wood,wood2,woodedge)
        poly([(x+w-3,y+4),(x+w+21,y-8),(x+w+21,y+h-14),(x+w-3,y+h)],side)
        poly([(x+5,y+4),(x+26,y-8),(x+w+21,y-8),(x+w-3,y+4)],white if metal else (251,223,180,255))
        face(x,y,w,h,c1,c2)
    if name in ['fridge','freezer']:
        body(64,28,125,195,True); rr(72,34,108,69 if name=='fridge' else 55,9,(230,245,251,255),edge)
        if name=='fridge':
            rr(72,110,108,105,9,(208,231,242,255),edge);rr(80,52,7,32,3,handle);rr(80,123,7,47,3,handle);rr(130,45,35,15,5,(53,94,119,255));line([(136,52),(144,52)],(159,236,239,255),2)
        else:
            for y in [96,137,178]:rr(73,y,106,35,5,(206,233,245,255),edge);rr(105,y+5,42,5,2,handle)
            for a,b in [((126,48),(126,78)),((113,55),(139,71)),((113,71),(139,55))]:line([a,b],(62,151,203,255),3)
    elif name=='wardrobe':
        body(45,28,153,194);rr(52,35,67,180,5,wood,woodedge);rr(123,35,67,180,5,(229,186,139,255),woodedge);rr(109,118,5,27,2,woodedge);rr(129,118,5,27,2,woodedge)
    elif name in ['drawer','cabinet']:
        body(40,84,160,136);rr(48,218,11,9,2,woodedge);rr(179,218,11,9,2,woodedge)
        if name=='drawer':
            for y in [91,132,173]:rr(47,y,146,36,5,wood,woodedge);rr(104,y+11,35,6,3,woodedge)
        else:
            for x in [47,124]:rr(x,91,69,119,5,wood,woodedge);rr(x+54 if x==47 else x+7,140,5,25,2,woodedge)
    elif name in ['pantry','shelf']:
        body(48,32,150,190);rr(57,42,132,171,6,(104,83,70,255));
        for y in [91,150,207]:rr(57,y,132,8,2,wood)
        for x,col,h in [(67,(115,180,170,255),30),(94,(244,184,104,255),42),(121,(176,171,222,255),33),(148,(235,131,120,255),38)]:rr(x,90-h,20,h,4,col);rr(x+3,90-h+4,14,6,2,white)
        for x,col,h in [(65,(227,157,122,255),35),(101,(139,178,221,255),41),(140,(230,200,142,255),34)]:rr(x,150-h,30,h,4,col)
        if name=='pantry':
            for x,col in [(70,(190,218,193,255)),(108,(237,176,114,255)),(148,(147,193,219,255))]:rr(x,170,26,35,6,col);rr(x,165,26,8,3,white)
        else:
            for x,col in [(67,(176,181,227,255)),(90,(235,160,154,255)),(118,(130,177,206,255)),(145,(222,187,120,255))]:rr(x,172,19,35,2,col)
    elif name=='box':
        body(40,102,160,114);rr(33,87,176,29,5,(235,194,142,255),woodedge);rr(94,133,52,20,5,(123,84,55,255));rr(100,137,40,10,4,(80,64,50,255))
    elif name=='washer':
        body(44,65,150,153,True);rr(51,74,136,31,5,white,edge);rr(60,82,40,11,3,steel);ellipse(151,79,18,18,handle);ellipse(78,116,84,84,white,edge,3);ellipse(90,128,60,60,(81,138,173,255),handle,3);ellipse(100,137,23,14,(170,218,237,255))
    else:
        body(53,77,145,142);rr(63,87,125,119,8,(221,201,175,255),woodedge);ellipse(106,123,38,38,(250,233,206,255),woodedge);line([(125,131),(125,152)],woodedge,4);line([(115,142),(135,142)],woodedge,4)
    im.resize((256,256),Image.Resampling.LANCZOS).save(P/f'{name}.png',optimize=True)
