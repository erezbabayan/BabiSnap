from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cairosvg, io
F='/usr/share/fonts/truetype/sand-box/google/Rubik/Rubik-VariableFont_wght.ttf'
def font(sz,w=400):
    f=ImageFont.truetype(F,sz,layout_engine=ImageFont.Layout.RAQM)
    try: f.set_variation_by_axes([w])
    except Exception: pass
    return f
def png(svg,px): return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode(),output_width=px,output_height=px))).convert('RGBA')
W,H=1080,2160; bg=Image.new('RGB',(W,H)); d=ImageDraw.Draw(bg)
for y in range(H): t=y/H; d.line([(0,y),(W,y)],fill=(int(25+40*t),int(22+30*t),int(60+60*t)))
bg=bg.convert('RGBA'); bg.alpha_composite(Image.new('RGBA',(W,H),(0,0,0,110)))
s=3; cw,ch=300*s,330*s; cx,cy=(W-cw)//2,(H-ch)//2
card=Image.new('RGBA',(cw,ch),(0,0,0,0)); c=ImageDraw.Draw(card); c.rounded_rectangle([0,0,cw-1,ch-1],radius=28*s,fill='#FFFBEA')
mid=cw//2; top=24*s+80*s
for r,a in [(78*s,'#FFF1C6'),(66*s,'#FFE38F')]:
    c.ellipse([mid-r,top-r,mid+r,top+r],fill=a)
L=png(open('widget-logo.svg').read(),100*s); card.alpha_composite(L,(mid-L.width//2,top-L.height//2))
c.text((mid,top+100*s),'מקשיב… מה לקבוע ביומן?',font=font(18*s,700),fill='#2A1B7A',anchor='mm',direction='rtl',language='he')
c.text((mid,top+140*s),'פגישה עם דני מחר בשתיים',font=font(15*s),fill='#7A5A10',anchor='mm',direction='rtl',language='he')
c.text((mid,top+190*s),'ביטול',font=font(15*s),fill='#6A5FA8',anchor='mm',direction='rtl',language='he')
sh=Image.new('RGBA',(cw+120,ch+120),(0,0,0,0)); ImageDraw.Draw(sh).rounded_rectangle([60,75,60+cw,75+ch],radius=84,fill=(0,0,0,140))
bg.alpha_composite(sh.filter(ImageFilter.GaussianBlur(25)),(cx-60,cy-60)); bg.alpha_composite(card,(cx,cy))
bg.convert('RGB').save('/workspace/babisnap-recording-preview.png')
