package Project.Common;

public class DMPayload extends Payload {
   
   
    public DMPayload() {
        setPayloadType(PayloadType.DM);
    }

    String sender;
    Long id;

    String receiver;

    public void setSender(String sender){
        this.sender = sender;
    }

    public void setReceiver(String receiver){
        this.receiver = receiver;
    }

    public void setId(Long id){
        this.id = id;
    }

    public Long getId(){
        return this.id;
    }
    
    public String getReceiver(){
        return this.receiver;
    }


    public String getSender(){
        return this.sender;
    }
    @Override
    public String toString() {
        return String.format("From [%s], Message[%s], ClientId[%s]", getSender(),
                getMessage(), getClientId());
    }
}
