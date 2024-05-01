package Project.Common;

public class MutePayload extends Payload{
    

    private String muter;
    private String mutee;
    private Long id;

    public MutePayload(){
        setPayloadType(PayloadType.MUTE);
        
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

    public Long getId(){
        return this.id;
    }

    public void setId(Long id){
        this.id = id;
    }
   
    @Override
    public String toString(){
        return String.format("User[%s], has been muted by [%s]",getMutee(), getMuter());
    }
}
