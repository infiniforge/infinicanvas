#!/usr/bin/env python3
"""Independent small fixtures, authored from the public format specifications."""
from pathlib import Path
import io,struct,zlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/authoring-fixtures'
OUT.mkdir(parents=True,exist_ok=True)
u16=lambda n:struct.pack('>H',n)
u32=lambda n:struct.pack('>I',n)
i32=lambda n:struct.pack('>i',n)
def chunk(tag,data):
    return u32(len(data))+tag+data+u32(zlib.crc32(tag+data)&0xffffffff)
def png(w,h,rgba=None):
    header=u32(w)+u32(h)+bytes([8,6 if rgba else 3,0,0,0])
    pixels=b''.join(b'\0'+(rgba[y*w*4:(y+1)*w*4] if rgba else bytes(range(w))) for y in range(h))
    extra=b'' if rgba else chunk(b'PLTE',bytes([255,0,0,255,0,0,0,0,255,255,255,255]))+chunk(b'tRNS',bytes([255,255,255,0]))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',header)+extra+chunk(b'IDAT',zlib.compress(pixels))+chunk(b'IEND',b'')
(OUT/'indexed.png').write_bytes(png(4,1))
def iffchunk(tag,data): return tag+u32(len(data))+data+(b'\0' if len(data)%2 else b'')
bmhd=u16(4)+u16(1)+u16(0)+u16(0)+bytes([2,2,0,0])+u16(3)+bytes([1,1])+u16(4)+u16(1)
# Indices 0,1,2,3 in two word-aligned bitplanes.
body=bytes([0x50,0,0x30,0])
crng=u16(0)+u16(16384)+u16(1)+bytes([0,2])
ccrt=struct.pack('>hBBIIH',1,0,2,0,16667,0)
form=b'ILBM'+iffchunk(b'BMHD',bmhd)+iffchunk(b'CMAP',bytes([255,0,0,0,255,0,0,0,255,255,255,255]))+iffchunk(b'CRNG',crng)+iffchunk(b'CCRT',ccrt)+iffchunk(b'ANNO',b'keep me')+iffchunk(b'BODY',body)
(OUT/'indexed.iff').write_bytes(b'FORM'+u32(len(form))+form)
drng=bytes([0,2])+u16(16384)+u16(1)+bytes([0,3,0,0,1,1,2,2])
dynamic=b'ILBM'+iffchunk(b'BMHD',bmhd)+iffchunk(b'CMAP',bytes([255,0,0,0,255,0,0,0,255,255,255,255]))+iffchunk(b'CRNG',crng)+iffchunk(b'DRNG',drng)+iffchunk(b'BODY',body)
(OUT/'dynamic.iff').write_bytes(b'FORM'+u32(len(dynamic))+dynamic)
overlap=b'ILBM'+iffchunk(b'BMHD',bmhd)+iffchunk(b'CMAP',bytes([255,0,0,0,255,0,0,0,255,255,255,255]))+iffchunk(b'CRNG',crng)+iffchunk(b'CRNG',u16(0)+u16(8192)+u16(1)+bytes([0,2]))+iffchunk(b'BODY',body)
(OUT/'overlapping.iff').write_bytes(b'FORM'+u32(len(overlap))+overlap)
TOP=bytes([255,0,0,128,255,0,0,128])
BOTTOM=bytes([0,0,255,255])*8
with zipfile.ZipFile(OUT/'layers.ora','w') as archive:
    archive.writestr('mimetype','image/openraster',compress_type=zipfile.ZIP_STORED)
    archive.writestr('stack.xml','<image w="4" h="2"><stack><layer name="Red α" src="data/top.png" x="1" y="0"/><layer name="Blue" src="data/bottom.png"/></stack></image>')
    archive.writestr('data/top.png',png(2,1,TOP));archive.writestr('data/bottom.png',png(4,2,BOTTOM))

def psd(compression):
    records=[];channels=[]
    for name,w,h,x,y,data in [('Red α',2,1,1,0,TOP),('Blue',4,2,0,0,BOTTOM)]:
        parts=[]
        for channel in range(4):
            raw=data[channel::4]
            if compression==0: encoded=raw
            elif compression==1:
                rows=[bytes([w-1])+raw[row*w:(row+1)*w] for row in range(h)]
                encoded=b''.join(u16(len(row)) for row in rows)+b''.join(rows)
            else:
                if compression==3:
                    raw=bytes((raw[i]-raw[i-1])&255 if i%w else raw[i] for i in range(len(raw)))
                encoded=zlib.compress(raw)
            parts.append(u16(compression)+encoded)
        pascal=name.encode('ascii','replace')
        pascal=bytes([len(pascal)])+pascal;pascal+=b'\0'*((-len(pascal))%4)
        unicode=u32(len(name))+name.encode('utf-16be')
        extra=u32(0)+u32(0)+pascal+b'8BIMluni'+u32(len(unicode))+unicode
        records.append(i32(y)+i32(x)+i32(y+h)+i32(x+w)+u16(4)+b''.join(struct.pack('>hI',c if c<3 else -1,len(parts[c])) for c in range(4))+b'8BIMnorm'+bytes([255,0,0,0])+u32(len(extra))+extra)
        channels.extend(parts)
    info=struct.pack('>h',2)+b''.join(records)+b''.join(channels)
    info+=b'\0'*(len(info)%2)
    masks=u32(len(info))+info+u32(0)
    merged=bytes([0]*8)*4
    return b'8BPS'+u16(1)+b'\0'*6+u16(4)+u32(2)+u32(4)+u16(8)+u16(3)+u32(0)+u32(0)+u32(len(masks))+masks+u16(0)+merged
for compression in range(4): (OUT/f'layers-{compression}.psd').write_bytes(psd(compression))

def xcf(compression,wide=False):
    ptrsize=8 if wide else 4
    ptr=lambda n:struct.pack('>Q' if wide else '>I',n)
    out=bytearray(b'gimp xcf '+(b'v011' if wide else b'file')+b'\0'+u32(4)+u32(2)+u32(0)+(u32(150) if wide else b''))
    def prop(kind,data):return u32(kind)+u32(len(data))+data
    out.extend(prop(17,bytes([compression]))+prop(0,b''))
    table=len(out);out.extend(ptr(0)*4) # two layers, layer terminator, channel terminator
    offsets=[]
    for name,w,h,x,y,data in [('Red α',2,1,1,0,TOP),('Blue',4,2,0,0,BOTTOM)]:
        offsets.append(len(out)); title=name.encode()+b'\0'
        out.extend(u32(w)+u32(h)+u32(1)+u32(len(title))+title)
        out.extend(prop(6,u32(255))+prop(8,u32(1))+prop(15,i32(x)+i32(y))+prop(7,u32(0))+prop(0,b''))
        hp=len(out);out.extend(ptr(0)+ptr(0))
        hierarchy=len(out);out[hp:hp+ptrsize]=ptr(hierarchy)
        out.extend(u32(w)+u32(h)+u32(4));lp=len(out);out.extend(ptr(0)+ptr(0))
        level=len(out);out[lp:lp+ptrsize]=ptr(level)
        out.extend(u32(w)+u32(h));tp=len(out);out.extend(ptr(0)+ptr(0))
        tile=len(out);out[tp:tp+ptrsize]=ptr(tile)
        if compression==0: out.extend(data)
        elif compression==1:
            for channel in range(4): out.extend(bytes([256-w*h])+data[channel::4])
        else: out.extend(zlib.compress(data))
    for i,offset in enumerate(offsets):out[table+i*ptrsize:table+(i+1)*ptrsize]=ptr(offset)
    return bytes(out)
for compression in range(3):
    for wide in [False,True]: (OUT/f'layers-{compression}-{8 if wide else 4}.xcf').write_bytes(xcf(compression,wide))
print(f'Wrote fixtures to {OUT}')
