import cairosvg, os
NAVY='#2A1B7A'; GOLD='#FFC531'
def rr(x,y,w,h,r): return f"M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z"
B="M28,18 H54 C68,18 76,26 76,35 C76,42 72,46 67,49 C74,52 79,57 79,66 C79,76 70,84 56,84 H28 Z"
HOLES="M40,28 H52 C59,28 63,31 63,36 C63,41 59,44 52,44 H40 Z M40,54 H54 C62,54 66,58 66,63 C66,69 62,73 54,73 H40 Z"
W1=' '.join(rr(43+i*5,36-h/2,2.6,h,1.3) for i,h in enumerate([4,9,6,3]))
W2=' '.join(rr(43+i*5,63.5-h/2,2.6,h,1.3) for i,h in enumerate([5,12,8,10,4]))
WAVES=W1+' '+W2
LAYERS=[(B,NAVY),(HOLES,GOLD),(WAVES,NAVY)]
S=0.62; TX=54-53.5*S; TY=54-51*S
R='/workspace/voice-gcal-v1/android/app/src/main/res/'; D=R+'drawable/'
def vec(body,w=108,h=108,vp=108):
    return f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="{w}dp" android:height="{h}dp" android:viewportWidth="{vp}" android:viewportHeight="{vp}">{body}</vector>\n'
def grp(s,tx,ty,layers):
    return f'<group android:translateX="{tx:.2f}" android:translateY="{ty:.2f}" android:scaleX="{s}" android:scaleY="{s}">'+''.join(f'<path android:fillColor="{c}" android:pathData="{p}"/>' for p,c in layers)+'</group>'
GV='<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108"><item android:offset="0" android:color="#FFFFD43B"/><item android:offset="1" android:color="#FFFFA41B"/></gradient></aapt:attr>'
open(D+'ic_fg.xml','w').write(vec(grp(S,TX,TY,LAYERS)))
open(D+'ic_bg.xml','w').write(vec(f'<path android:pathData="M0,0H108V108H0Z">{GV}</path>'))
# monochrome: B with holes cut (evenOdd) + waves
open(D+'ic_monochrome.xml','w').write(vec(grp(S,TX,TY,[]).replace('</group>',f'<path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{B} {HOLES}"/><path android:fillColor="#FFFFFFFF" android:pathData="{WAVES}"/></group>')))
# widget logo (rounded square, B larger)
s2=0.8; open(D+'widget_logo.xml','w').write(vec(f'<path android:pathData="{rr(0,0,108,108,24)}">{GV}</path>'+grp(s2,54-53.5*s2,54-51*s2,LAYERS),48,48))
for f in ('widget_logo_old',):pass
# svg/png
GS='<linearGradient id="g" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFD43B"/><stop offset="1" stop-color="#FFA41B"/></linearGradient>'
def svg(shape,s=S,crop=(0,108)):
    a,b=crop; bgs={'round':f'<rect x="{a}" y="{a}" width="{b-a}" height="{b-a}" rx="{(b-a)*0.22}" fill="url(#g)"/>','circle':f'<circle cx="54" cy="54" r="{(b-a)/2}" fill="url(#g)"/>'}[shape]
    g=f'<g transform="translate({54-53.5*s},{54-51*s}) scale({s})">'+''.join(f'<path d="{p}" fill="{c}"/>' for p,c in LAYERS)+'</g>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{a} {a} {b-a} {b-a}"><defs>{GS}</defs>{bgs}{g}</svg>'
for dens,px in dict(mdpi=48,hdpi=72,xhdpi=96,xxhdpi=144,xxxhdpi=192).items():
    cairosvg.svg2png(bytestring=svg('round',crop=(9,99)).encode(),write_to=R+f'mipmap-{dens}/ic_launcher.png',output_width=px,output_height=px)
    cairosvg.svg2png(bytestring=svg('circle',crop=(9,99)).encode(),write_to=R+f'mipmap-{dens}/ic_launcher_round.png',output_width=px,output_height=px)
L=svg('round',crop=(9,99)); open('babisnap-logo.svg','w').write(L)
cairosvg.svg2png(bytestring=L.encode(),write_to='/workspace/babisnap-logo.png',output_width=1024,output_height=1024)
cairosvg.svg2png(bytestring=L.encode(),write_to='playstore-512.png',output_width=512,output_height=512)
WSVG=svg('round',s=0.8,crop=(0,108)); open('widget-logo.svg','w').write(WSVG)
# launcher-identical tile: visible 72x72 region of the 108 adaptive canvas (18..90), rounded square
c=f'<group android:translateX="-18" android:translateY="-18">'+grp(S,TX,TY,LAYERS)+'</group>'
GV72=GV.replace('android:endX="108" android:endY="108"','android:endX="72" android:endY="72"')
open(D+'widget_logo.xml','w').write(vec(f'<path android:pathData="{rr(0,0,72,72,16)}">{GV72}</path>'+c,48,48,72))
TILE=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><defs>{GS}</defs><rect x="18" y="18" width="72" height="72" rx="16" fill="url(#g)"/><g transform="translate({TX},{TY}) scale({S})">'+''.join(f'<path d="{p}" fill="{c}"/>' for p,c in LAYERS)+'</g></svg>'
open('widget-logo.svg','w').write(TILE)
