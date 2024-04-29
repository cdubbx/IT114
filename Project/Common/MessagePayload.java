package Project.Common;

public class MessagePayload extends Payload {
    
    String sender;

    public String getSender(){
        return sender;
    }

    public void setSender(String sender){
        this.sender = sender;
    }

    @Override
    public String toString(){
        return String.format("Sender [%s], [%s]", getSender(), getMessage());
    }

}
