package Project.Common;

public class UnMutePayload extends Payload{
    

    private String muter;
    private String mutee;
    public UnMutePayload(String sender, String receiver){
        setPayloadType(PayloadType.UNMUTE);
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
        return String.format("User[s%], has been unmuted by [s%]", getMuter(), getMutee());
    }
}
