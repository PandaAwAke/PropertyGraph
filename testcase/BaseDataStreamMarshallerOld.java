class BaseDataStreamMarshallerOld {

    private Throwable createThrowable(String className, String message) {
        try {
            Class clazz = Class.forName(className, false, BaseDataStreamMarshaller.class.getClassLoader());
            Constructor constructor = clazz.getConstructor(new Class[] {String.class});
            return (Throwable)constructor.newInstance(new Object[] {message});
        } catch (Throwable e) {
            return new Throwable(className + ": " + message);
        }
    }

}