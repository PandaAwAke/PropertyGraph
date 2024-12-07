public abstract class BaseDataStreamMarshaller implements DataStreamMarshaller {
    protected DataStructure tightUnmarsalCachedObject(OpenWireFormat wireFormat, DataInput dataIn,
                                                      BooleanStream bs) throws IOException {
        if (wireFormat.isCacheEnabled()) {
            if (bs.readBoolean()) {
                short index = dataIn.readShort();
                DataStructure object = wireFormat.tightUnmarshalNestedObject(dataIn, bs);
                wireFormat.setInUnmarshallCache(index, object);
                return object;
            } else {
                short index = dataIn.readShort();
                return wireFormat.getFromUnmarshallCache(index);
            }
        } else {
            return wireFormat.tightUnmarshalNestedObject(dataIn, bs);
        }
    }
}