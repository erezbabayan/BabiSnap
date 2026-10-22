from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cairosvg, io
F='/usr/share/fonts/truetype/sand-box/google/Rubik/Rubik-VariableFont_wght.ttf'
fnt=lambda s:ImageFont.truetype(F,s,layout_engine=ImageFont.Layout.RAQM)
def png(svg,px): return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode(),output_width=px,output_height=px))).convert('RGBA')
D=open('/workspace/babisnap-record-D.png','rb').read()
recD=lambda px: Image.open(io.BytesIO(D)).convert('RGBA').resize((px,px),Image.LANCZOS)
LS=open('widget-logo.svg').read()
def labeled(tile,label,s,rtl):
    w=int(80*s); h=int(80*s); im=Image.new('RGBA',(w,h),(0,0,0,0))
    im.alpha_composite(tile,((w-tile.width)//2,int(4*s)))
    txt=Image.new('RGBA',(w,h),(0,0,0,0)); kw=dict(font=fnt(int(12*s)),anchor='mm',**({'direction':'rtl','language':'he'} if rtl else {}))
    ImageDraw.Draw(txt).text((w//2,int(4*s)+tile.height+int(12*s)),label,fill=(0,0,0,150),**kw)
    txt=txt.filter(ImageFilter.GaussianBlur(s)); im.alpha_composite(txt)
    ImageDraw.Draw(im).text((w//2,int(4*s)+tile.height+int(11*s)),label,fill='white',**kw)
    return im
# picker preview (3x)
recD(168).save('/workspace/voice-gcal-v1/android/app/src/main/res/drawable-nodpi/widget_button_preview.png')
W,H=1080,1300; bg=Image.new('RGB',(W,H)); d=ImageDraw.Draw(bg)
for y in range(H): t=y/H; d.line([(0,y),(W,y)],fill=(int(30+60*t),int(25+35*t),int(70+80*t)))
bg=bg.convert('RGBA')
o=Image.new('RGBA',(W,H),(0,0,0,0)); ImageDraw.Draw(o).ellipse([-200,200,600,1000],fill=(255,180,40,60)); bg.alpha_composite(o.filter(ImageFilter.GaussianBlur(140)))
d=ImageDraw.Draw(bg); d.text((W//2,120),'BabiSnap 1.3',font=fnt(56),fill='white',anchor='mm')
s=4.2
a=labeled(png(LS,int(52*s)),'BabiSnap',s,False); b=Image.new('RGBA',a.size,(0,0,0,0)); b.alpha_composite(recD(int(52*s)),((a.width-int(52*s))//2,int(4*s)))
gap=120; x=(W-a.width-b.width-gap)//2; y=380
bg.alpha_composite(a,(x,y)); bg.alpha_composite(b,(x+a.width+gap,y))
d=ImageDraw.Draw(bg)
d.text((x+a.width//2,y+a.height+70),'App icon',font=fnt(34),fill=(210,210,235),anchor='mm')
d.text((x+a.width+gap+b.width//2,y+b.height+70),'Record widget 1×1',font=fnt(34),fill=(210,210,235),anchor='mm')
bg.convert('RGB').save('/workspace/babisnap-v13-preview.png')
