package Project.Common;

public class UnMutePayload extends Payload{
    

    private String muter;
    private String mutee;
    private Long id;
    public UnMutePayload(){
        setPayloadType(PayloadType.UNMUTE);
      
    }

    public void setId(Long id){
        this.id = id;
    }

    public Long getId(){
        return this.id;
    }
    
    public void setMuter(String muter){
        this.muter = muter;
    }
    public void setMutee(String mutee){
        this.mutee = mutee;
    }
    public String getMuter(){
        if(this.muter == null){
            return "Anonymous";
        }
        return this.muter;
    }
    public String getMutee(){
        return this.mutee;
    }

    @Override
    public String toString(){
        return String.format("User[%s], has been unmuted by [%s]",getMutee(), getMuter());
    }
}
