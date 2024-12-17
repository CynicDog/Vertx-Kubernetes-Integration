package cynicdog.io.data;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

import java.util.ArrayList;
import java.util.List;

public  class Embedding {

    @ProtoField(number = 1, collectionImplementation = ArrayList.class)
    List<Float> latentScores;

    @ProtoField(number = 2)
    String document;

    @ProtoFactory
    public Embedding(List<Float> latentScores, String document) {
        this.latentScores = latentScores;
        this.document = document;
    }

    public List<Float> getLatentScores() {
        return latentScores;
    }

    public void setLatentScores(List<Float> latentScores) {
        this.latentScores = latentScores;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }
}