package com.maodun.photoswipe;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/** Pure Java regression: no Android runtime required. */
public final class LivePhotoMetadataTest {
    private static void expect(String input, boolean expected) {
        byte[] bytes=input.getBytes(StandardCharsets.ISO_8859_1);
        boolean result=LivePhotoMetadata.inspect(bytes,bytes.length).live;
        if(result!=expected)throw new AssertionError(input+" => "+result);
    }
    public static void main(String[] args) throws Exception {
        expect("<rdf:Description Camera:MotionPhoto=\"1\"/>",true);
        expect("<rdf:Description Camera:MotionPhoto=\"0\"/>",false);
        expect("<rdf:Description Camera:MotionPhoto=\"false\"/>",false);
        expect("<Camera:MicroVideo>true</Camera:MicroVideo>",true);
        expect("<rdf:Description Vendor:LivePhoto=\"1\"/>",true);
        expect("<rdf:Description Camera:MicroVideoOffset=\"100\"/>",true);
        expect("<rdf:Description Camera:MicroVideoOffset=\"0\"/>",false);
        expect("<Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\"/>",true);
        expect("This is an ordinary HONOR image with live music description",false);
        expect("<rdf:Description Camera:MotionPhotoVersion=\"1\"/>",false);
        if(args.length>0){
            byte[] bytes=Files.readAllBytes(Paths.get(args[0]));
            LivePhotoMetadata.Result result=LivePhotoMetadata.inspect(bytes,Math.min(bytes.length,524288));
            if(result.live)throw new AssertionError("Unmarked user JPEG must not be falsely flagged");
            System.out.println("User JPEG: "+result.evidence);
        }
        System.out.println("PASS: 10 metadata cases + unmarked original JPEG");
    }
}
