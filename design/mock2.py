from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cairosvg, io
F='/usr/share/fonts/truetype/sand-box/google/Rubik/Rubik-VariableFont_wght.ttf'
def font(sz,w=400):
    f=ImageFont.truetype(F,sz,layout_engine=ImageFont.Layout.RAQM)
    try: f.set_variation_by_axes([w])
    except Exception: pass
    return f
WS=open('widget-logo.svg').read(); LS=open('babisnap-logo.svg').read()
def png(svg,px): return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode(),output_width=px,output_height=px))).convert('RGBA')
def wide(s):
    W,H=int(220*s),int(72*s); im=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(im)
    d.rounded_rectangle([0,0,W-1,H-1],radius=int(28*s),fill=(255,246,220,245))
    L=png(WS,int(48*s)); im.alpha_composite(L,(int(10*s),(H-L.height)//2)); x=int(68*s)
    d.text((x,int(14*s)),'BabiSnap',font=font(int(17*s),700),fill='#2A1B7A')
    d.text((x,int(40*s)),'הקש ודבר · פגישה ליומן',font=font(int(12*s)),fill='#7A5A10',direction='rtl',language='he')
    return im
def btn(s): return png(WS,int(56*s))
R='/workspace/voice-gcal-v1/android/app/src/main/res/drawable-nodpi/'
wide(3).save(R+'widget_preview.png'); btn(3).save(R+'widget_button_preview.png')
W,H=1080,2160; bg=Image.new('RGB',(W,H)); d=ImageDraw.Draw(bg)
for y in range(H):
    t=y/H; d.line([(0,y),(W,y)],fill=(int(25+40*t),int(22+30*t),int(60+60*t)))
bg=bg.convert('RGBA')
for cx,cy,r,c in [(220,520,380,(255,180,40,70)),(880,1400,450,(120,80,255,80))]:
    o=Image.new('RGBA',(W,H),(0,0,0,0)); ImageDraw.Draw(o).ellipse([cx-r,cy-r,cx+r,cy+r],fill=c); bg.alpha_composite(o.filter(ImageFilter.GaussianBlur(120)))
d=ImageDraw.Draw(bg)
d.text((60,40),'14:24',font=font(40,500),fill='white')
d.text((W//2,330),'14:24',font=font(200,300),fill='white',anchor='mm')
d.text((W//2,470),'יום חמישי, 22 באוקטובר',font=font(44),fill=(235,235,255),anchor='mm',direction='rtl',language='he')
def place(im,x,y,rad):
    sh=Image.new('RGBA',(im.width+80,im.height+80),(0,0,0,0)); ImageDraw.Draw(sh).rounded_rectangle([40,50,40+im.width,50+im.height],radius=rad,fill=(0,0,0,110))
    bg.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)),(x-40,y-40)); bg.alpha_composite(im,(x,y))
w=wide(3); place(w,(W-w.width)//2,680,84)
d.text((W//2,680+w.height+50),'2×1 · BabiSnap',font=font(32),fill=(220,220,240),anchor='mm')
b=btn(3.2); place(b,(W-b.width)//2,1020,60)
d.text((W//2,1020+b.height+50),'1×1 · Button',font=font(32),fill=(220,220,240),anchor='mm')
cols=['#34A853','#EA4335','#4285F4']; names=['Phone','Messages','Chrome']; ic=150; gap=(W-4*ic)//5
full=png(LS.replace('viewBox="9 9 90 90"','viewBox="18 18 72 72"'),ic); m=Image.new('L',(ic,ic),0); ImageDraw.Draw(m).ellipse([0,0,ic,ic],fill=255); full.putalpha(m)
for i in range(4):
    x=gap+i*(ic+gap); y=1720
    if i==0: bg.alpha_composite(full,(x,y)); n='BabiSnap'
    else: d.ellipse([x,y,x+ic,y+ic],fill=cols[i-1]); n=names[i-1]
    d.text((x+ic//2,y+ic+38),n,font=font(34),fill='white',anchor='mm')
d.rounded_rectangle([60,2040,W-60,2110],radius=35,fill=(255,255,255,60))
bg.convert('RGB').save('/workspace/babisnap-widgets-preview.png')
