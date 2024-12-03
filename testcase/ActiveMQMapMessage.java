import java.util.HashMap;
import java.util.Map;

import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;

import org.apache.activemq.util.ByteSequence;
import org.fusesource.hawtbuf.UTF8Buffer;

public class ActiveMQMapMessage extends ActiveMQMessage implements MapMessage {

    public static final byte DATA_STRUCTURE_TYPE = CommandTypes.ACTIVEMQ_MAP_MESSAGE;

    protected transient Map<String, Object> map = new HashMap<String, Object>();

    @SuppressWarnings("unchecked")
    protected <T> T doGetBody(Class<T> asType) throws JMSException {
        storeContent();
        final ByteSequence content = getContent();
        final Map<String, Object> map = content != null ? deserialize(content) : null;

        //This implementation treats an empty map as not having a body so if empty
        //we should return null as well
        if (map != null && !map.isEmpty()) {
            map.replaceAll((k, v) -> v instanceof UTF8Buffer ? v.toString() : v);
            return (T) map;
        } else {
            return null;
        }
    }
    
}