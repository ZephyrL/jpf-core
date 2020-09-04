public class Cross {

    enum Direction {N, E, S, W};
    
    enum Road {NW, NE, SW, SE};

    static Road nwRoad = Road.NW;
    static Road neRoad = Road.NE;
    static Road swRoad = Road.SW;
    static Road seRoad = Road.SE;

    static class Car extends Thread {

        Direction dir;

        public Car(Direction dir) {
            this.dir = dir;
            setName(dir.name());
        }

        @Override
        public void run() {
            switch(dir) {
                case N:
                    synchronized (nwRoad) {
                        synchronized(swRoad){
                            // cross
                        }
                    }
                    break;
                case S:
                    synchronized(seRoad){
                        synchronized(neRoad){
                            // cross
                        }
                    }
                    break;
                case E: 
                    synchronized(neRoad){
                        synchronized(nwRoad){
                            // cross
                        }
                    }
                    break;
                case W:
                    synchronized(swRoad){
                        synchronized(seRoad){
                            // cross
                        }
                    }
                    break;
            }
        } 
    }

    public static void main(String[] args) {
        Car carW = new Car(Direction.W);
        Car carE = new Car(Direction.E);
        Car carN = new Car(Direction.N);
        Car carS = new Car(Direction.S);

        carW.start();
        carE.start();
        carN.start();
        carS.start();
    }
}