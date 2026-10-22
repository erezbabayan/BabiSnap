from PIL import Image, ImageDraw, ImageFont
F='/usr/share/fonts/truetype/sand-box/google/Rubik/Rubik-VariableFont_wght.ttf'
fnt=lambda s:ImageFont.truetype(F,s,layout_engine=ImageFont.Layout.RAQM)
R='/workspace/voice-gcal-v1/android/app/src/main/res/drawable-nodpi/'
tile=Image.open('/workspace/babisnap-record-D.png').convert('RGBA'); wide=Image.open(R+'widget_preview.png').convert('RGBA')
s=3
def fit(im,w,h):
    r=min(w/im.width,h/im.height); return im.resize((int(im.width*r),int(im.height*r)),Image.LANCZOS)
def dialog(dark):
    W,H=int(320*s),int(200*s); bgc=(40,40,44) if dark else (252,252,255); fg=(235,235,240) if dark else (25,25,30)
    im=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(im); d.rounded_rectangle([0,0,W-1,H-1],radius=26*s,fill=bgc)
    d.text((W-24*s,30*s),'איזה יישומון להוסיף?',font=fnt(20*s),fill=fg,anchor='rm',direction='rtl',language='he')
    y=56*s; cards=[(tile,88),(wide,176)]; total=sum(w for _,w in cards)*s+12*s; x=(W+total)//2
    for img,w in cards:  # RTL: first card on the right
        x-=w*s; ov=Image.new('RGBA',(W,H),(0,0,0,0)); ImageDraw.Draw(ov).rounded_rectangle([x,y,x+w*s,y+88*s],radius=20*s,fill=(128,128,128,26),outline=(128,128,128,64),width=s); im.alpha_composite(ov)
        t=fit(img,w*s-20*s,68*s); im.alpha_composite(t,(x+(w*s-t.width)//2,y+(88*s-t.height)//2)); x-=12*s
    ImageDraw.Draw(im).text((28*s,H-26*s),'ביטול',font=fnt(16*s),fill=(90,140,255) if dark else (40,90,220),anchor='lm',direction='rtl',language='he')
    return im
W,H=2200,1100; bg=Image.new('RGBA',(W,H),(30,26,60,255)); d=ImageDraw.Draw(bg)
d.rectangle([0,0,W//2,H],fill=(225,228,238)); d.rectangle([W//2,0,W,H],fill=(14,14,18))
for i,dk in enumerate([False,True]):
    dl=dialog(dk); bg.alpha_composite(dl,(i*W//2+(W//2-dl.width)//2,200))
    d.text((i*W//2+W//4,100),'Dark mode' if dk else 'Light mode',font=fnt(44),fill=(230,230,240) if dk else (40,40,60),anchor='mm')
bg.convert('RGB').save('/workspace/babisnap-picker-preview.png')
