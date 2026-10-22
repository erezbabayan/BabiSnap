from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cairosvg, io, re
F='/usr/share/fonts/truetype/sand-box/google/Rubik/Rubik-VariableFont_wght.ttf'
def font(sz,w):
    f=ImageFont.truetype(F,sz,layout_engine=ImageFont.Layout.RAQM)
    try: f.set_variation_by_axes([w])
    except Exception: pass
    return f
svg=open('babisnap-logo.svg').read()
def logo(px): return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode(),output_width=px,output_height=px))).convert('RGBA')
def widget(s):  # s = px per dp
    W,H=int(220*s),int(72*s)
    im=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(im)
    d.rounded_rectangle([0,0,W-1,H-1],radius=int(28*s),fill=(248,248,255,245))
    L=logo(int(48*s)); im.alpha_composite(L,(int(10*s),(H-L.height)//2))
    x=int(68*s)
    d.text((x,int(14*s)),'BabiSnap',font=font(int(17*s),700),fill='#2A1B7A')
    d.text((x,int(40*s)),'הקש ודבר · פגישה ליומן',font=font(int(12*s),400),fill='#6A5FA8',direction='rtl',language='he')
    return im
widget(3).save('/workspace/voice-gcal-v1/android/app/src/main/res/drawable-nodpi/widget_preview.png') if __import__('os').makedirs('/workspace/voice-gcal-v1/android/app/src/main/res/drawable-nodpi',exist_ok=True) is None else 0
# home screen mockup
s=3; W,H=1080,2160
bg=Image.new('RGB',(W,H)); d=ImageDraw.Draw(bg)
for y in range(H):
    t=y/H; d.line([(0,y),(W,y)],fill=(int(30+60*t),int(20+40*t),int(70+90*t)))
bg=bg.convert('RGBA'); d=ImageDraw.Draw(bg)
for cx,cy,r,c in [(200,500,380,(120,80,255,90)),(900,1400,450,(30,180,255,80))]:
    o=Image.new('RGBA',(W,H),(0,0,0,0)); ImageDraw.Draw(o).ellipse([cx-r,cy-r,cx+r,cy+r],fill=c); bg.alpha_composite(o.filter(ImageFilter.GaussianBlur(120)))
d=ImageDraw.Draw(bg)
d.text((60,40),'14:11',font=font(40,500),fill='white')
d.text((W//2,330),'14:11',font=font(200,300),fill='white',anchor='mm')
d.text((W//2,470),'יום חמישי, 22 באוקטובר',font=font(44,400),fill=(230,230,255),anchor='mm',direction='rtl',language='he')
w=widget(s); sh=Image.new('RGBA',(w.width+80,w.height+80),(0,0,0,0)); ImageDraw.Draw(sh).rounded_rectangle([40,50,40+w.width,50+w.height],radius=84,fill=(0,0,0,110))
wx=(W-w.width)//2; wy=700
bg.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)),(wx-40,wy-40)); bg.alpha_composite(w,(wx,wy))
# app row placeholders + dock
cols=['#34A853','#EA4335','#FBBC05','#4285F4']
names=['Phone','Messages','Photos','Chrome']
ic=150; gap=(W-4*ic)//5
for i in range(4):
    x=gap+i*(ic+gap); y=1700
    d.ellipse([x,y,x+ic,y+ic],fill=cols[i])
    d.text((x+ic//2,y+ic+38),names[i],font=font(34,400),fill='white',anchor='mm')
x=gap; y=1400; L=logo(ic+30); m=Image.new('L',L.size,0); ImageDraw.Draw(m).ellipse([0,0,L.width,L.height],fill=255)
# adaptive-like circle crop: render full-bleed from 108 viewbox
full=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.replace('viewBox="9 9 90 90"','viewBox="18 18 72 72"').encode(),output_width=ic,output_height=ic))).convert('RGBA')
m=Image.new('L',(ic,ic),0); ImageDraw.Draw(m).ellipse([0,0,ic,ic],fill=255); full.putalpha(m)
bg.alpha_composite(full,(x,y)); d.text((x+ic//2,y+ic+38),'BabiSnap',font=font(34,400),fill='white',anchor='mm')
d.rounded_rectangle([60,2040,W-60,2110],radius=35,fill=(255,255,255,60))
bg.convert('RGB').save('/workspace/babisnap-widget-preview.png')
