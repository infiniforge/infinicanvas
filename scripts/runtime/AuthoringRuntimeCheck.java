import android.graphics.*;
import com.infinifolder.preview.*;
import com.infinicanvas.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real Android pixels and real APK classes; no Activity or fake Bitmap implementation. */
public class AuthoringRuntimeCheck {
    static File root;
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static void pass(String name) { System.out.println("PASS authoring."+name); }
    static byte[] bytes(String name) throws Exception { return Files.readAllBytes(new File(root,name).toPath()); }
    static Bitmap png(byte[] data) { Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length);check(b!=null,"PNG decoded");return b; }
    static byte[] payload(byte[] bytes,String tag,boolean png) throws Exception {
        int at=png?8:12;
        while(at+8<=bytes.length) {
            int n=((bytes[at+(png?0:4)]&255)<<24)|((bytes[at+(png?1:5)]&255)<<16)|((bytes[at+(png?2:6)]&255)<<8)|(bytes[at+(png?3:7)]&255);
            String name=new String(bytes,at+(png?4:0),4,"US-ASCII");
            if(name.equals(tag)) return Arrays.copyOfRange(bytes,at+8,at+8+n);
            at+=8+n+(png?4:n%2);
        }
        throw new AssertionError("Missing chunk "+tag);
    }
    static void near(int actual,int expected,String message) {
        for(int shift:new int[]{0,8,16,24})check(Math.abs((actual>>>shift&255)-(expected>>>shift&255))<=1,message+" got "+Integer.toHexString(actual));
    }
    static BitmapFrame iff(byte[] bytes) { BitmapFrame f=CanvasStreams.decodeBytes(bytes,"fixture.iff",2048,true);check(f!=null,"IFF decoded");return f; }
    static void layers(LayerDocument doc,String format) {
        check(doc!=null && doc.getWidth()==4 && doc.getHeight()==2,format+" canvas");
        check(doc.getLayers().size()==2,format+" actual separate layers");
        ImageLayer top=doc.getLayers().get(0),bottom=doc.getLayers().get(1);
        check(top.getName().equals("Red α") && bottom.getName().equals("Blue"),format+" names");
        check(top.getX()==1 && top.getY()==0 && top.getBitmap().getWidth()==2,format+" offsets/size");
        near(top.getBitmap().getPixel(0,0),0x80ff0000,format+" original layer alpha");
        Bitmap merged=LayerDocuments.INSTANCE.composite(doc);
        near(merged.getPixel(0,0),0xff0000ff,format+" bottom outside layer");
        near(merged.getPixel(1,0),0xff80007f,format+" source-over with alpha");merged.recycle();
    }
    public static void main(String[] args) {
        try {
            root=new File(args[0]);
            byte[] original=bytes("indexed.png");
            PaletteDocument palette=PaletteDocument.Companion.read(original);
            check(palette!=null && palette.getEditableColors()==4,"PNG palette size");
            check(palette.getColors().get(0).equals(palette.getColors().get(1)),"fixture duplicate entries");
            List<Integer> colors=new ArrayList<>(palette.getColors());colors.set(1,0xff00ff00);
            byte[] changed=palette.encode(colors,palette.getCycles());
            check(Arrays.equals(payload(original,"IDAT",true),payload(changed,"IDAT",true)),"encoded indices unchanged");
            Bitmap decoded=png(changed);
            check(decoded.getPixel(0,0)==0xffff0000 && decoded.getPixel(1,0)==0xff00ff00 && decoded.getPixel(2,0)==0xff0000ff,"duplicate slots edited independently");
            check(decoded.getPixel(3,0)>>>24==0,"indexed PNG alpha");pass("indexed-png-identity-and-alpha");

            byte[] iffOriginal=bytes("indexed.iff"); PaletteDocument indexed=PaletteDocument.Companion.read(iffOriginal);
            check(indexed!=null && indexed.getCycles().size()==1,"CRNG/CCRT compatibility ranges deduplicated");
            colors=new ArrayList<>(indexed.getColors());colors.set(1,0xffffff00);
            PaletteDocument.Cycle cycle=new PaletteDocument.Cycle(0,2,10000,false,true);
            byte[] cycled=indexed.encode(colors,List.of(cycle));
            check(Arrays.equals(payload(iffOriginal,"BODY",false),payload(cycled,"BODY",false)),"IFF BODY unchanged");
            check(Arrays.equals(payload(iffOriginal,"ANNO",false),payload(cycled,"ANNO",false)),"IFF unknown chunk retained");
            PaletteDocument reopened=PaletteDocument.Companion.read(cycled);
            check(reopened.getColors().get(1)==0xffffff00 && reopened.getCycles().size()==1,"IFF saved palette/range");
            BitmapFrame frame=iff(cycled);check(frame.getColorCycling()!=null,"saved cycle is playable");
            int[] output=new int[4];frame.getColorCycling().render(125000000L,output,true);
            check(output[0]==0xff0000ff && output[1]==0xffff0000 && output[2]==0xffffff00,"forward palette rotation");
            check(output[3]>>>24==0,"transparent index during cycle");pass("iff-forward-cycle-and-body-preservation");
            byte[] reversed=indexed.encode(colors,List.of(new PaletteDocument.Cycle(0,2,10000,true,true)));
            frame=iff(reversed);frame.getColorCycling().render(125000000L,output,true);
            check(output[0]==0xffffff00 && output[1]==0xff0000ff,"reverse palette rotation");pass("iff-reverse-cycle");
            byte[] disabled=indexed.encode(colors,List.of(new PaletteDocument.Cycle(0,2,10000,false,false)));
            frame=iff(disabled);check(frame.getColorCycling()==null,"disabled CRNG also removes active CCRT copy");
            check(frame.getBitmap().getPixel(3,0)>>>24==0,"transparent index remains when cycling is stopped/disabled");pass("iff-disabled-cycle-and-transparency");

            byte[] dynamic=bytes("dynamic.iff"); PaletteDocument dynamicPalette=PaletteDocument.Companion.read(dynamic);
            byte[] dynamicChanged=dynamicPalette.encode(dynamicPalette.getColors(),List.of(new PaletteDocument.Cycle(0,2,10000,false,true)));
            check(Arrays.equals(payload(dynamic,"DRNG",false),payload(dynamicChanged,"DRNG",false)),"DRNG cells remain unchanged");
            String dynamicChunks=new String(dynamicChanged,"ISO-8859-1");
            check(dynamicChunks.indexOf("CRNG")<dynamicChunks.indexOf("DRNG"),"regular range remains before DRNG");
            pass("iff-drng-preservation-and-range-order");
            PaletteDocument overlapping=PaletteDocument.Companion.read(bytes("overlapping.iff"));
            check(overlapping.getCycles().size()==2,"independent CRNG records with equal endpoints retained");
            List<PaletteDocument.Cycle> editedRanges=new ArrayList<>(overlapping.getCycles());editedRanges.set(0,new PaletteDocument.Cycle(0,2,10000,false,true));
            PaletteDocument overlapAgain=PaletteDocument.Companion.read(overlapping.encode(overlapping.getColors(),editedRanges));
            check(overlapAgain.getCycles().size()==2 && overlapAgain.getCycles().get(1).getMillihertz()==30000,"editing one overlapping range preserves the other");
            pass("iff-distinct-overlapping-ranges");
            LayerDocument ora=LayerDocuments.INSTANCE.decode(bytes("layers.ora"),2048);layers(ora,"ORA");pass("ora-separate-layer-pixels");
            for(int compression=0;compression<4;compression++) {
                layers(LayerDocuments.INSTANCE.decode(bytes("layers-"+compression+".psd"),2048),"PSD "+compression);
            }pass("psd-raw-rle-zip-prediction");
            for(int compression=0;compression<3;compression++)for(int width:new int[]{4,8}) {
                layers(LayerDocuments.INSTANCE.decode(bytes("layers-"+compression+"-"+width+".xcf"),2048),"XCF "+compression+"/"+width);
            }pass("xcf-raw-rle-zlib-and-64-bit-offsets");
            LayerDocument roundtrip=LayerDocuments.INSTANCE.decode(LayerDocuments.INSTANCE.encodeOra(ora),2048);
            layers(roundtrip,"ORA roundtrip");pass("ora-layer-export-roundtrip");

            Bitmap base=LayerDocuments.INSTANCE.composite(ora);
            ImageLayer top=ora.getLayers().get(0);
            Edit hide=new Edit.LayerProperties(top.getId(),top.getName(),false,255,1,0,"normal");
            DocumentRenderer.Result hidden=DocumentRenderer.INSTANCE.render(base,ora,null,List.of(hide),root);
            check(hidden.getDocument().getLayers().size()==2,"hidden layer retained");
            check(hidden.getBitmap().getPixel(1,0)==0xff0000ff,"hidden layer excluded from composition");
            check(ora.getLayers().get(0).getVisible(),"original layer state unchanged");pass("layer-visibility-without-source-mutation");
            List<Edit> operations=List.of(new Edit.LayerDuplicate(top.getId(),"copy"),new Edit.LayerMove("copy",2),
                new Edit.OnLayer(top.getId(),new Edit.Adjust(0,0,-100)),new Edit.LayerDelete("copy"));
            DocumentRenderer.Result adjusted=DocumentRenderer.INSTANCE.render(base,ora,null,operations,root);
            check(adjusted.getDocument().getLayers().size()==2,"duplicate/order/delete sequence");
            check(adjusted.getDocument().getLayers().get(1).getBitmap().getPixel(0,0)==0xff0000ff,"adjustment affects selected layer only");
            check(ora.getLayers().get(0).getBitmap().getPixel(0,0)==0x80ff0000,"original layer pixels unchanged");
            check(adjusted.getDocument().getLayers().get(0).getBitmap().getPixel(0,0)!=0x80ff0000,"selected layer changed");pass("layer-duplicate-reorder-delete-and-targeted-adjustment");
            org.json.JSONArray serialized=EditCodec.INSTANCE.write(operations);
            check(EditCodec.INSTANCE.read(new org.json.JSONArray(serialized.toString())).equals(operations),"layer recipe survives JSON recovery");pass("authoring-edit-recipe-roundtrip");
            Bitmap paletteBase=png(original);
            DocumentRenderer.Result rotated=DocumentRenderer.INSTANCE.render(paletteBase,null,palette,List.of(new Edit.PaletteShift(0,2,1)),root);
            check(rotated.getPalettePreservable(),"palette-only edits remain indexed");
            check(Arrays.equals(payload(original,"IDAT",true),payload(rotated.getPaletteBytes(),"IDAT",true)),"palette rotation retains indices");
            DocumentRenderer.Result raster=DocumentRenderer.INSTANCE.render(paletteBase,null,palette,List.of(new Edit.Turn(true)),root);
            check(!raster.getPalettePreservable(),"raster geometry cannot be silently omitted from indexed export");pass("palette-rotation-and-raster-export-guard");
            byte[] malformed=bytes("layers-0.psd");malformed=Arrays.copyOf(malformed,malformed.length/2);
            boolean failed=false;try { LayerDocuments.INSTANCE.decode(malformed,2048); }catch(Exception expected){failed=true;}
            check(failed,"truncated layered project rejected");pass("malformed-project-rejection");
            ContainerRuntimeCheck.run(root);
            System.out.println("AUTHORING RUNTIME CHECKS PASSED");
            System.exit(0);
        } catch(Throwable error) { error.printStackTrace();System.exit(1); }
    }
}
