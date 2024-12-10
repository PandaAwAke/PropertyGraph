class MyTest {

//    public void test1(int x) {
//        switch (x) {
//            case 1 -> System.out.println(1);
//            case 2 -> System.out.println(2);
//            default -> System.out.println(3);
//        }
//    }

//    public void test2(int x) {
//        int j = 0;
//        for (int i = 1; i < 100; i++, j++) {
//            if (i < 50) {
//                continue;
//            }
//        }
//
//        System.out.println("Finished");
//    }

    public void test3(int x) {
        Integer total = (Integer)request.getSession(true).getAttribute("total");    // should NO_DEF request
        Integer total = (Integer)request.getSession(true).setAttribute("total");    // should MAY_DEF request
        List.of(1, 2);
        ++total;

        String retval[];
        retval = retval[1].split("with|where", 2);
        String tablename = retval[0];
        retval = retval[1].split("where", 2);
    }

}