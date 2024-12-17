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

//    public void test3(int x) {
//        Integer total = (Integer)request.getSession(true).getAttribute("total");    // should NO_DEF request
//        Integer total = (Integer)request.getSession(true).setAttribute("total");    // should MAY_DEF request
//        List.of(1, 2);
//        ++total;
//
//        String retval[];
//        retval = retval[1].split("with|where", 2);
//        String tablename = retval[0];
//        retval = retval[1].split("where", 2);
//    }

//    /**
//     * This is a javadoc
//     * @param a
//     * @param b
//     */
//    @Override
//    public void test4(Object a, Object b) {
//        this.t = 0;     // lhs = FieldAccess (lhs.expression = ThisExpression, lhs.fieldName = "t")
//        super.p = 1;    // lhs = SuperFieldAccess (lhs.fieldName = "p")
//        String retval[];
//
//        retval[1].split("with|where", 2);
//        m.get()[1] = 2;
//
//        a.y = 1;        // QualifiedName or FieldAccess
//        b.x = 2;        // QualifiedName or FieldAccess
//        a.x = b.y;      // QualifiedName or FieldAccess
//        foo().bar = 2;      // FieldAccess
//        a.p.q.r = 4;
//    }

    public void test5(int x) {
        final ProcessGroup parentGroup = parent.get();

        x = 1;
        {
            System.out.println(x);
            int x = 2;
            System.out.println(x);
            {
                x = 3;
                System.out.println(x);
            }
        }
    }

}