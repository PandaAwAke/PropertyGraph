class BaseDataStreamMarshallerNew {

    private Throwable createThrowable(String className, String message) {
        try {
            Class clazz = Class.forName(className, false, BaseDataStreamMarshaller.class.getClassLoader());
            OpenWireUtil.validateIsThrowable(clazz);
            Constructor constructor = clazz.getConstructor(new Class[] {String.class});
            return (Throwable)constructor.newInstance(new Object[] {message});
        } catch (IllegalArgumentException e) {
            return e;
        } catch (Throwable e) {
            return new Throwable(className + ": " + message);
        }
    }

}