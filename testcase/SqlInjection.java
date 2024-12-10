class SqlInjection {
    public List<String> getUserList() {
        List<String> userlist = new ArrayList<String>();
        A ps = null;
        String authQuery = "";
        String retval[];
        String tablename = "";
        String username = "";
        String userquery = "";

        retval = new String[] {"a", "b"};
        if (retval.length >= 2) {
            retval = retval[1].split("with|where", 2);
            tablename = retval[0];
            retval = retval[1].split("where", 2);
            if (retval.length >= 2)
                retval = retval[1].split("=", 2);
            else
                retval = retval[0].split("=", 2);
            username = retval[0];
        }

        userquery = "select " + username + " from " + tablename;
        ps = new A(userquery);

        return userlist;
    }
}