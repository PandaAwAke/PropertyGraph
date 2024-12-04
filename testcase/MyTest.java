class MyTest {

    public void test1(int x) {
        switch (x) {
            case 1 -> System.out.println(1);
            case 2 -> System.out.println(2);
            default -> System.out.println(3);
        }
    }

    public void test2(int x) {
        for (int i = 1; i < 100; i++) {
            if (i < 50) {
                continue;
            }

            if (i > 70) {
                break;
            }
        }

        System.out.println("Finished");
    }

    public void test3(int x) {
        x = 1;
        x = 2;
    }

}