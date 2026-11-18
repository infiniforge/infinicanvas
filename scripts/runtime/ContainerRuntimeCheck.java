import android.graphics.Bitmap;
import android.net.Uri;
import com.infinicanvas.*;
import com.infinifolder.preview.*;
import io.github.gretchycat.infinistream.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class ContainerRuntimeCheck {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static void pass(String name) { System.out.println("PASS containers."+name); }
    static byte[] zip(Map<String,byte[]> entries) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)) { for(var e:entries.entrySet()) { zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry(); } }return out.toByteArray();
    }
    static Map<String,byte[]> unzip(byte[] bytes) throws Exception {
        Map<String,byte[]> out=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))) { ZipEntry e;while((e=zip.getNextEntry())!=null) { out.put(e.getName(),zip.readAllBytes());zip.closeEntry(); } }return out;
    }
    static byte[] save(OpenRasterDocument doc) { ByteArrayOutputStream out=new ByteArrayOutputStream();ImageSaving.writeOpenRaster(doc,out);return out.toByteArray(); }
    static byte[] flic(boolean fli) throws Exception {
        List<byte[]> frames=new ArrayList<>();
        for(int i=0;i<2;i++) {
            ByteBuffer palette=ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN);palette.putInt(13).putShort((short)(fli?11:4)).putShort((short)1).put((byte)1).put((byte)1);
            palette.put((byte)(i==0?(fli?63:255):0)).put((byte)(i==1?(fli?63:255):0)).put((byte)0);
            ByteBuffer frame=ByteBuffer.allocate(i==0?39:29).order(ByteOrder.LITTLE_ENDIAN);
            frame.putInt(frame.capacity()).putShort((short)0xf1fa).putShort((short)(i==0?2:1)).putShort((short)(fli?0:80));frame.position(16);frame.put(palette.array());
            if(i==0) frame.putInt(10).putShort((short)16).put(new byte[]{1,1,1,1});frames.add(frame.array());
        }
        ByteBuffer header=ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);header.putInt(196).putShort((short)(fli?0xaf11:0xaf12)).putShort((short)2).putShort((short)2).putShort((short)2).putShort((short)8);header.position(16);header.putInt(fli?2:80);
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(header.array());for(byte[] f:frames)out.write(f);return out.toByteArray();
    }
    public static void run(File root) throws Exception {
        byte[] iff=Files.readAllBytes(new File(root,"indexed.iff").toPath());
        String xml="<image w='4' h='2' xmlns:x='urn:custom' x:author='keep'><x:metadata value='keep'/><stack><stack name='Group' x='1' opacity='0.5'><layer src='media/indexed.iff' name='Cycle' x:custom='keep'/></stack><layer src='audio/track.bin' name='Audio' visibility='hidden'/></stack></image>";
        Map<String,byte[]> entries=new LinkedHashMap<>();entries.put("stack.xml",xml.getBytes());entries.put("media/indexed.iff",iff);entries.put("audio/track.bin",new byte[]{1,2,3,4});entries.put("other/file.future",new byte[]{5,6,7});
        OpenRasterDocument mixed=OpenRasterDocuments.decode(zip(entries));check(mixed!=null && mixed.getDocument().getLayers().size()==3,"each media gets a layer");
        check(mixed.getAssets().values().stream().filter(a->!a.getRenderable()).count()==2,"opaque layers preserved");
        byte[] saved=save(mixed);Map<String,byte[]> after=unzip(saved);
        for(var e:entries.entrySet())if(!e.getKey().equals("stack.xml"))check(Arrays.equals(e.getValue(),after.get(e.getKey())),"original payload "+e.getKey());
        check(new String(after.get("stack.xml")).contains("x:author=\"keep\"") && new String(after.get("stack.xml")).contains("x:custom=\"keep\""),"unknown XML retained");
        check(OpenRasterDocuments.decode(saved).getDocument().getLayers().size()==3,"reopen media layers");pass("mixed-ora-payload-and-xml-preservation");
        Bitmap base=OpenRasterDocuments.composite(mixed);check((base.getPixel(1,0)>>>24)>0 && (base.getPixel(1,0)>>>24)<255,"group opacity");pass("nested-group-composition");
        String id=mixed.getDocument().getLayers().get(0).getId();
        DocumentRenderer.Result palette=DocumentRenderer.INSTANCE.render(base,mixed.getDocument(),null,List.of(new Edit.OnLayer(id,new Edit.PaletteColor(0,0xffffff00))),root,null,mixed);
        var paletteAgain=OpenRasterDocuments.decode(save(palette.getOra()));
        check(paletteAgain.getAssets().values().stream().anyMatch(a->a.getPalette()!=null && a.getPalette().getColors().get(0)==0xffffff00),"embedded palette edit survives");
        check(paletteAgain.getAssets().values().stream().anyMatch(a->a.getFrame()!=null && a.getFrame().getColorCycling()!=null),"embedded cycling survives");pass("embedded-iff-palette-and-cycle-save");
        var raster=DocumentRenderer.INSTANCE.render(base,mixed.getDocument(),null,List.of(new Edit.OnLayer(id,new Edit.Flip(true))),root,null,mixed);
        var rasterEntries=unzip(save(raster.getOra()));check(Arrays.equals(iff,rasterEntries.get("media/indexed.iff")),"original IFF retained after raster editing");check(new String(rasterEntries.get("stack.xml")).contains("original-src"),"working PNG references original");pass("raster-working-copy-keeps-original-source");
        File importedSource=new File(root,"asset-00000000-0000-0000-0000-000000000000.bin");Files.write(importedSource.toPath(),iff);
        var imported=DocumentRenderer.INSTANCE.render(base,null,null,List.of(new Edit.MediaImport("imported",importedSource.getName(),"Imported IFF","iff",null),new Edit.OnLayer("imported",new Edit.Flip(true))),root);
        check(Arrays.equals(iff,unzip(save(imported.getOra())).get("data/imported.iff")),"newly imported non-PNG original survives raster editing");pass("imported-source-survives-working-copy-export");
        String opaque=mixed.getDocument().getLayers().get(1).getId();boolean rejected=false;
        try { DocumentRenderer.INSTANCE.render(base,mixed.getDocument(),null,List.of(new Edit.OnLayer(opaque,new Edit.Flip(true))),root,null,mixed); }catch(Exception expected) { rejected=true; }check(rejected,"opaque pixel edit must not replace bytes");
        var deleted=DocumentRenderer.INSTANCE.render(base,mixed.getDocument(),null,List.of(new Edit.LayerDelete(opaque)),root,null,mixed);check(!unzip(save(deleted.getOra())).containsKey("audio/track.bin"),"deleted media stays deleted");pass("opaque-edit-guard-and-explicit-delete");
        List<AnimationFrame> frames=List.of(new AnimationFrame(base,40),new AnimationFrame(base,90));
        AnimationDocument animation=new AnimationDocument(4,2,frames,3);
        byte[] apng=AnimationDocuments.INSTANCE.encodeApng(animation);var apngAgain=AnimationDocuments.INSTANCE.decode(apng,Integer.MAX_VALUE);
        check(apngAgain.getFrames().size()==2 && apngAgain.getFrames().get(1).getDelayMillis()==90 && apngAgain.getPlays()==3,"APNG timing round trip");pass("apng-frame-and-loop-roundtrip");
        for(boolean fli:new boolean[]{true,false}) {
            var decoded=AnimationDocuments.INSTANCE.decode(flic(fli),Integer.MAX_VALUE);
            check(decoded.getFrames().size()==2,"FLIC frames");check(decoded.getFrames().get(0).getBitmap().getPixel(0,0)==0xffff0000,"FLIC first palette");check(decoded.getFrames().get(1).getBitmap().getPixel(0,0)==0xff00ff00,"FLIC delta palette frame");check(decoded.getFrames().get(1).getDelayMillis()==(fli?29:80),"FLIC timing");
        }pass("fli-flc-indexed-delta-animation-import");
        AnimationProject project=new AnimationProject(4,2,List.of(new AnimationProjectFrame("a","First",mixed,40),new AnimationProjectFrame("b","Second",mixed,90)),3,Map.of("notes.txt",new byte[]{9,8}));
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageSaving.writeAnimationProject(project,output);byte[] projectBytes=output.toByteArray();
        var unpack=unzip(projectBytes);check(unpack.containsKey("animation.xml") && unpack.containsKey("frames/0.ora") && unpack.containsKey("frames/1.ora"),"ZIP XML ORA structure");
        var reopened=AnimationProjects.decode(projectBytes);check(reopened.getFrames().size()==2 && reopened.getPlays()==3,"timeline round trip");check(reopened.getFrames().get(0).getDocument().getDocument().getLayers().size()==3,"frame ORA layers retained");check(Arrays.equals(reopened.getAttachments().get("notes.txt"),new byte[]{9,8}),"attachments retained");pass("icanim-zip-xml-ora-timeline-roundtrip");
        List<Edit> edits=List.of(new Edit.FrameEdit("a",new Edit.OnLayer(id,new Edit.PaletteColor(0,0xff00ffff))),new Edit.FrameDelay("b",125),new Edit.FrameLoops(2),new Edit.LayerDuplicate("a","c"),new Edit.LayerMove("c",2));
        check(EditCodec.INSTANCE.read(EditCodec.INSTANCE.write(edits)).equals(edits),"nested animation edit recovery");
        var timeline=DocumentRenderer.INSTANCE.render(base,null,null,edits,root,null,null,project);
        check(timeline.getProject().getFrames().size()==3 && timeline.getFrames().getPlays()==2,"dedicated timeline edits");
        check(timeline.getProject().getFrames().get(0).getDocument().getAssets().get(id).getPalette().getColors().get(0)==0xff00ffff,"targeted frame layer edited");
        check(timeline.getProject().getFrames().get(1).getDocument().getAssets().get(id).getPalette().getColors().get(0)!=0xff00ffff,"other frame not changed");pass("independent-frame-layers-and-edit-recovery");
        check(ImageSignatures.INSTANCE.extension(projectBytes).equals("icanim"),"project byte identity");
        check(CanvasCapabilities.INSTANCE.getExtensions().contains("icanim"),"registered viewer extension");check(ImageSaving.INSTANCE.format("icanim").getAnimation(),"registered writer");
        var decoder=new ThumbnailDecoder(null,(uri)->new ByteArrayInputStream(projectBytes));
        var preview=decoder.decodeViewerFrame(Uri.parse("infinicanvas://test/project.icanim"),16,"icanim");check(preview!=null && preview.getAnimation()!=null,"shared viewer playback");
        check(CanvasStreams.decodeBytes(projectBytes,"movie.icanim",16,true).getAnimation()!=null,"common byte source playback");
        int[] outputPixels=new int[preview.getBitmap().getWidth()*preview.getBitmap().getHeight()];preview.getAnimation().render(70000000L,outputPixels,true);preview.getAnimation().render(999999999L,outputPixels,true);
        check(decoder.decodeImage(Uri.parse("infinicanvas://test/opaque-resource"),16,null,null)!=null,"extensionless shared capability decoder");
        ByteArrayOutputStream animationOutput=new ByteArrayOutputStream();ImageSaving.writeAnimation(animation,"icanim",animationOutput);check(AnimationProjects.decode(animationOutput.toByteArray()).getFrames().size()==2,"existing animation writer call supports new target");
        var handler=new AnimationProjectHandler();check(handler.decode(projectBytes,16,"icanim")!=null,"shared thumbnail decoder");pass("library-identity-discovery-thumbnail-and-playback");
        ByteArrayOutputStream stream=new ByteArrayOutputStream(){boolean closed=false;public void close(){closed=true;}public void write(int v){if(closed)throw new AssertionError("stream closed");super.write(v);}};
        ImageSaving.writeAnimationProject(project,stream);stream.write(1);pass("borrowed-output-stream-remains-open");
    }
}
