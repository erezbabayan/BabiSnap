import cairosvg
# shared shapes in 108 viewport; group scaled 0.84 about center
FG = [ # (path, fill, stroke, strokeWidth)
 ("M39,34 H69 A7,7 0 0 1 76,41 V71 A7,7 0 0 1 69,78 H39 A7,7 0 0 1 32,71 V41 A7,7 0 0 1 39,34 Z","#FFFFFF",None,0),
 ("M39,34 H69 A7,7 0 0 1 76,41 V46 H32 V41 A7,7 0 0 1 39,34 Z","#FF5A6E",None,0),
 ("M42,28 A2,2 0 0 1 44,30 V38 A2,2 0 0 1 40,38 V30 A2,2 0 0 1 42,28 Z","#2A1B7A",None,0),
 ("M66,28 A2,2 0 0 1 68,30 V38 A2,2 0 0 1 64,38 V30 A2,2 0 0 1 66,28 Z","#2A1B7A",None,0),
 ("M54,50 A5,5 0 0 1 59,55 V62 A5,5 0 0 1 49,62 V55 A5,5 0 0 1 54,50 Z","#5B3DF5",None,0),
 ("M45.5,61 A8.5,8.5 0 0 0 62.5,61 M54,69.5 V73.5 M49.5,73.5 H58.5",None,"#5B3DF5",3),
 ("M80,72 A11,11 0 1 1 58,72 A11,11 0 1 1 80,72 Z","#FFC531","#FFFFFF",2.5),
 ("M71,63.5 L63.5,73.5 H68.5 L66.5,80.5 L74.5,70 H69.5 Z","#2A1B7A",None,0),
]
S=0.84; T=54*(1-S)
def svg_fg():
    s=f'<g transform="translate({T},{T}) scale({S})">'
    for d,f,st,w in FG:
        s+=f'<path d="{d}" fill="{f or "none"}"'+(f' stroke="{st}" stroke-width="{w}" stroke-linecap="round"' if st else '')+'/>'
    return s+'</g>'
GRAD='<defs><linearGradient id="g" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#6A3DF5"/><stop offset="1" stop-color="#1FB6FF"/></linearGradient></defs>'
def svg(shape):
    clip={'square':'<rect width="108" height="108" fill="url(#g)"/>',
          'circle':'<circle cx="54" cy="54" r="54" fill="url(#g)"/>',
          'round':'<rect x="0" y="0" width="108" height="108" rx="24" fill="url(#g)"/>'}[shape]
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{GRAD}{clip}{svg_fg()}</svg>'
# legacy icon shows full 108 canvas zoomed so content fills ~; use viewBox crop 9..99
def legacy(shape):
    return svg(shape).replace('viewBox="0 0 108 108"','viewBox="9 9 90 90"').replace('r="54"','r="45"').replace('<rect x="0" y="0" width="108" height="108" rx="24"','<rect x="9" y="9" width="90" height="90" rx="20"')
import os
R='/workspace/voice-gcal-v1/android/app/src/main/res/'
for dens,px in dict(mdpi=48,hdpi=72,xhdpi=96,xxhdpi=144,xxxhdpi=192).items():
    os.makedirs(R+f'mipmap-{dens}',exist_ok=True)
    cairosvg.svg2png(bytestring=legacy('round').encode(),write_to=R+f'mipmap-{dens}/ic_launcher.png',output_width=px,output_height=px)
    cairosvg.svg2png(bytestring=legacy('circle').encode(),write_to=R+f'mipmap-{dens}/ic_launcher_round.png',output_width=px,output_height=px)
open('babisnap-logo.svg','w').write(legacy('round'))
cairosvg.svg2png(bytestring=legacy('round').encode(),write_to='/workspace/babisnap-logo.png',output_width=1024,output_height=1024)
cairosvg.svg2png(bytestring=legacy('round').encode(),write_to='playstore-512.png',output_width=512,output_height=512)
# android vectors
def vec(w,h,body,vp=108):
    return f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="{w}dp" android:height="{h}dp" android:viewportWidth="{vp}" android:viewportHeight="{vp}">{body}</vector>\n'
def vfg():
    s=f'<group android:translateX="{T}" android:translateY="{T}" android:scaleX="{S}" android:scaleY="{S}">'
    for d,f,st,w in FG:
        s+=f'<path android:pathData="{d}"'+(f' android:fillColor="{f}"' if f else '')+(f' android:strokeColor="{st}" android:strokeWidth="{w}" android:strokeLineCap="round"' if st else '')+'/>'
    return s+'</group>'
GV='<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108"><item android:offset="0" android:color="#FF6A3DF5"/><item android:offset="1" android:color="#FF1FB6FF"/></gradient></aapt:attr>'
D=R+'drawable/'
open(D+'ic_fg.xml','w').write(vec(108,108,vfg()))
open(D+'ic_bg.xml','w').write(vec(108,108,f'<path android:pathData="M0,0H108V108H0Z">{GV}</path>'))
open(D+'ic_monochrome.xml','w').write(vec(108,108,vfg().replace('#FFFFFF','#FFFFFFFF').replace('#FF5A6E','#FFFFFF').replace('#2A1B7A','#FFFFFF').replace('#5B3DF5','#000000').replace('#FFC531','#FFFFFF')))
# widget logo: rounded square + content, viewport 108
open(D+'widget_logo.xml','w').write(vec(48,48,f'<path android:pathData="M24,0H84A24,24 0 0 1 108,24V84A24,24 0 0 1 84,108H24A24,24 0 0 1 0,84V24A24,24 0 0 1 24,0Z">{GV}</path>'+vfg().replace(f'android:scaleX="{S}" android:scaleY="{S}"','android:scaleX="1.12" android:scaleY="1.12"').replace(f'android:translateX="{T}" android:translateY="{T}"',f'android:translateX="{54*(1-1.12)}" android:translateY="{54*(1-1.12)}"')))
mono=''.join(f'<path android:pathData="{d}"'+(' android:fillColor="#FFFFFFFF"' if f else ' android:strokeColor="#FFFFFFFF" android:strokeWidth="3" android:strokeLineCap="round"')+'/>' for i,(d,f,st,w) in enumerate(FG) if i in (0,4,5)) 
# monochrome: calendar outline-free: card + mic cut not possible simply -> use mic + bolt only, enlarged
mono=''.join(f'<path android:pathData="{FG[i][0]}"'+(' android:fillColor="#FFFFFFFF"' if FG[i][1] else ' android:strokeColor="#FFFFFFFF" android:strokeWidth="3" android:strokeLineCap="round"')+'/>' for i in (4,5,7))
open(D+'ic_monochrome.xml','w').write(vec(108,108,f'<group android:translateX="-14" android:translateY="-22" android:scaleX="1.3" android:scaleY="1.3">{mono}</group>'))
