package Project.Common;

public class MutePayload extends Payload{
    

    private String muter;
    private String mutee;
    public MutePayload(String sender, String receiver){
        setPayloadType(PayloadType.MUTE);
        this.muter = sender;
        this.mutee = receiver;
    }
    
    public String getMuter(){
        return this.muter;
    }

    public String getMutee(){
        return this.mutee;
    }

    @Override
    public String toString(){
        return String.format("User[s%], has been muted by [s%]", getMuter(), getMutee());
    }
}
