from PIL import Image, ImageDraw, ImageFont, ImageFilter
import cairosvg, io, re
F='/usr/share/fonts/truetype/sand-box/google/Rubik/Rubik-VariableFont_wght.ttf'
fnt=lambda s:ImageFont.truetype(F,s)
T=open('widget-logo.svg').read()
inner=re.search(r'<g transform.*</g>',T).group(0); GS=re.search(r'<defs>.*</defs>',T).group(0)
def svg(body,defs=GS): return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">{defs}{body}</svg>'
tile='<rect x="18" y="18" width="72" height="72" rx="16" fill="url(#g)"/>'
MIC='<g transform="translate({x},{y})"><circle r="9" fill="#2A1B7A" stroke="#fff" stroke-width="2"/><rect x="-2.6" y="-5.5" width="5.2" height="8" rx="2.6" fill="#FFC531"/><path d="M-4.5,0 A4.5,4.5 0 0 0 4.5,0 M0,4.5 V6.5" stroke="#FFC531" stroke-width="1.5" fill="none" stroke-linecap="round"/></g>'
small=lambda: inner.replace('scale(0.62)','scale(0.52)').replace(re.search(r'translate\(([^)]*)\)',inner).group(0),f'translate({54-53.5*0.52},{54-51*0.52})')
V={
'A':("Thick navy frame",svg('<rect x="18" y="18" width="72" height="72" rx="16" fill="#2A1B7A"/><rect x="24" y="24" width="60" height="60" rx="11" fill="url(#g)"/>'+small())),
'B':("Red recording dot",svg(tile+inner+'<circle cx="80" cy="28" r="7.5" fill="#FF3B4E" stroke="#fff" stroke-width="2.5"/>')),
'C':("Inverted: navy tile, yellow B",svg('<rect x="18" y="18" width="72" height="72" rx="16" fill="#2A1B7A"/>'+inner.replace('#2A1B7A','#TMP').replace('#FFC531','#2A1B7A').replace('#TMP','#FFC531'))),
'D':("White inner ring + mic badge",svg(tile+'<rect x="23" y="23" width="62" height="62" rx="12" fill="none" stroke="#fff" stroke-width="2.5"/>'+small()+MIC.format(x=79,y=79))),
}
def png(s,px): return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=s.encode(),output_width=px,output_height=px))).convert('RGBA')
W,H=1600,1100; bg=Image.new('RGB',(W,H)); d=ImageDraw.Draw(bg)
for y in range(H): t=y/H; d.line([(0,y),(W,y)],fill=(int(25+40*t),int(22+30*t),int(60+60*t)))
bg=bg.convert('RGBA'); d=ImageDraw.Draw(bg)
d.text((W//2,60),'BabiSnap – 1×1 record widget variants',font=fnt(48),fill='white',anchor='mm')
ic=220; launcher=png(T,ic)
for i,(k,(lab,s)) in enumerate(V.items()):
    x0=60+(i%2)*780; y0=140+(i//2)*470
    o=Image.new('RGBA',(W,H),(0,0,0,0)); ImageDraw.Draw(o).rounded_rectangle([x0,y0,x0+700,y0+430],radius=30,fill=(255,255,255,28)); bg.alpha_composite(o); d=ImageDraw.Draw(bg)
    d.text((x0+30,y0+40),f'{k}. {lab}',font=fnt(38),fill='white',anchor='lm')
    for j,(im,cap) in enumerate([(launcher,'App icon'),(png(s,ic),'Record widget')]):
        x=x0+110+j*260; y=y0+95
        sh=Image.new('RGBA',(ic+60,ic+60),(0,0,0,0)); ImageDraw.Draw(sh).rounded_rectangle([30,40,30+ic,40+ic],radius=48,fill=(0,0,0,120))
        bg.alpha_composite(sh.filter(ImageFilter.GaussianBlur(14)),(x-30,y-30)); bg.alpha_composite(im,(x,y)); d=ImageDraw.Draw(bg)
        d.text((x+ic//2,y+ic+40),cap,font=fnt(30),fill=(225,225,245),anchor='mm')
    png(s,512).save(f'/workspace/babisnap-record-{k}.png')
bg.convert('RGB').save('/workspace/babisnap-record-variants.png')
