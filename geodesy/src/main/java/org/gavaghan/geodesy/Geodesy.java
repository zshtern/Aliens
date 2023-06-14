package org.gavaghan.geodesy;


// TODO: decide on geodesy library out of Geodesy.java/gavaghan and remove the other


// Methods in this class are based on http://www.movable-type.co.uk/scripts/
public class Geodesy
{
    public static final double EARTH_RADIUS = 6371e3;
    public static final float GALL_PETERS_RANGE_X = 800;
    public static final float GALL_PETERS_RANGE_Y = 512;

    /**
     * Returns the distance from 'this' point to destination point (using haversine formula).
     *
     * @param   {LatLon} point - Latitude/longitude of destination point.
     * @param   {number} [radius=6371e3] - (Mean) radius of earth (defaults to radius in metres).
     * @returns {number} Distance between this point and destination point, in same units as radius.
     *
     * @example
     *     double p1 = new LatLon(52.205, 0.119), p2 = new LatLon(48.857, 2.351);
     *     double d = p1.distanceTo(p2); // Number(d.toPrecision(4)): 404300
     */
    public static double distanceBetween(GlobalCoordinates pointStart, GlobalCoordinates pointEnd) {

        double R = EARTH_RADIUS;
        double fi1 = Angle.toRadians(pointStart.getLatitude()), gamma1 = Angle.toRadians(pointStart.getLongitude());
        double fi2 = Angle.toRadians(pointEnd.getLatitude()), gamma2 = Angle.toRadians(pointEnd.getLongitude());
        double delta_fi = fi2 - fi1;
        double delta_gamma = gamma2 - gamma1;

        double a = Math.sin(delta_fi/2) * Math.sin(delta_fi/2) +
                Math.cos(fi1) * Math.cos(fi2) *
                        Math.sin(delta_gamma/2) * Math.sin(delta_gamma/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }


    public static double distanceBetween2d(double []pointStart, double []pointEnd) {

        return Math.sqrt(Math.pow(pointEnd[0] - pointStart[0], 2) + Math.pow(pointEnd[1] - pointStart[1], 2));
    }


    /**
     * Returns the (initial) bearing from 'this' point to destination point.
     *
     * @param   {LatLon} point - Latitude/longitude of destination point.
     * @returns {number} Initial bearing in degrees from north.
     *
     * @example
     *     double p1 = new LatLon(52.205, 0.119), p2 = new LatLon(48.857, 2.351);
     *     double b1 = p1.bearingTo(p2); // b1.toFixed(1): 156.2
     */
    public static double bearingTo(GlobalCoordinates pointStart, GlobalCoordinates pointEnd) {

        double fi1 = Angle.toRadians(pointStart.getLatitude()), fi2 = Angle.toRadians(pointEnd.getLatitude());
        double delta_gamma = Angle.toRadians(pointEnd.getLongitude() - pointStart.getLongitude());

        // see http://mathforum.org/library/drmath/view/55417.html
        double y = Math.sin(delta_gamma) * Math.cos(fi2);
        double x = Math.cos(fi1)*Math.sin(fi2) -
                Math.sin(fi1)*Math.cos(fi2)*Math.cos(delta_gamma);
        double theta = Math.atan2(y, x);

        return (Angle.toDegrees(theta) + 360) % 360;
    }

    /**
     * Returns the destination point from 'this' point having travelled the given distance on the
     * given initial bearing (bearing normally varies around path followed).
     *
     * @param   {number} distance - Distance travelled, in same units as earth radius (default: metres).
     * @param   {number} bearing - Initial bearing in degrees from north.
     * @param   {number} [radius=6371e3] - (Mean) radius of earth (defaults to radius in metres).
     * @returns {LatLon} Destination point.
     *
     * @example
     *     double p1 = new LatLon(51.4778, -0.0015);
     *     double p2 = p1.destinationPoint(7794, 300.7); // p2.toString(): 51.5135�N, 000.0983�W
     */
    public static GlobalCoordinates destinationPoint(GlobalCoordinates start, double distance, double bearing) {

        double R = EARTH_RADIUS;

        // see http://williams.best.vwh.net/avform.htm#LL

        double lamda = distance / R; // angular distance in radians
        double theta = Angle.toRadians(bearing);

        double fi1 = Angle.toRadians(start.getLatitude());
        double gamma1 = Angle.toRadians(start.getLongitude());

        double fi2 = Math.asin(Math.sin(fi1) * Math.cos(lamda) + Math.cos(fi1) * Math.sin(lamda) * Math.cos(theta) );
        double gamma2 = gamma1 + Math.atan2(Math.sin(theta)*Math.sin(lamda)*Math.cos(fi1),
                                 Math.cos(lamda)-Math.sin(fi1)*Math.sin(fi2));
        gamma2 = (gamma2+3*Math.PI) % (2*Math.PI) - Math.PI; // normalise to -180..+180�

        return new GlobalCoordinates(Angle.toDegrees(fi2), Angle.toDegrees(gamma2));
    }

    public static void destination2dPoint(double []start, double distance, double A, boolean direction, double []result) {

        if (!direction)
            distance = -distance;

        double atanA = Math.atan(A);
        result[0] = start[0] + distance * Math.cos(atanA);
        result[1] = start[1] + distance * Math.sin(atanA);
    }

        /**
         * Returns the point of intersection of two paths defined by point and bearing.
         *
         * @param   {LatLon} p1 - First point.
         * @param   {number} brng1 - Initial bearing from first point.
         * @param   {LatLon} p2 - Second point.
         * @param   {number} brng2 - Initial bearing from second point.
         * @returns {LatLon} Destination point (null if no unique intersection defined).
         *
         * @example
         *     double p1 = LatLon(51.8853, 0.2545), brng1 = 108.547;
         *     double p2 = LatLon(49.0034, 2.5735), brng2 =  32.435;
         *     double pInt = LatLon.intersection(p1, brng1, p2, brng2); // pInt.toString(): 50.9078�N, 004.5084�E
         */
    public static GlobalCoordinates intersection(GlobalCoordinates p1, double brng1, GlobalCoordinates p2, double brng2) {

        // see http://williams.best.vwh.net/avform.htm#Intersection

        double fi1 = Angle.toRadians(p1.getLatitude()), gamma1 = Angle.toRadians(p1.getLongitude());
        double fi2 = Angle.toRadians(p2.getLatitude()), gamma2 = Angle.toRadians(p2.getLongitude());
        double theta13 = Angle.toRadians(brng1), theta23 = Angle.toRadians(brng2);
        double delta_fi = fi2-fi1, delta_gamma = gamma2-gamma1;

        double lamda12 = 2*Math.asin( Math.sqrt( Math.sin(delta_fi/2)*Math.sin(delta_fi/2) +
            Math.cos(fi1)*Math.cos(fi2)*Math.sin(delta_gamma/2)*Math.sin(delta_gamma/2) ) );
        if (lamda12 == 0) return null;

        // initial/final bearings between points
        double theta1 = Math.acos( ( Math.sin(fi2) - Math.sin(fi1)*Math.cos(lamda12) ) /
                            ( Math.sin(lamda12)*Math.cos(fi1) ) );
        if (Double.isNaN(theta1)) theta1 = 0; // protect against rounding
        double theta2 = Math.acos( ( Math.sin(fi1) - Math.sin(fi2)*Math.cos(lamda12) ) /
                            ( Math.sin(lamda12)*Math.cos(fi2) ) );

        double theta12,theta21;
        if (Math.sin(gamma2-gamma1) > 0) {
            theta12 = theta1;
            theta21 = 2*Math.PI - theta2;
        } else {
            theta12 = 2*Math.PI - theta1;
            theta21 = theta2;
        }

        double alpha1 = (theta13 - theta12 + Math.PI) % (2*Math.PI) - Math.PI; // angle 2-1-3
        double alpha2 = (theta21 - theta23 + Math.PI) % (2*Math.PI) - Math.PI; // angle 1-2-3

        if (Math.sin(alpha1)==0 && Math.sin(alpha2)==0) return null; // infinite intersections
        if (Math.sin(alpha1)*Math.sin(alpha2) < 0) return null;      // ambiguous intersection

        //alpha1 = Math.abs(alpha1);
        //alpha2 = Math.abs(alpha2);
        // ... Ed Williams takes abs of alpha1/alpha2, but seems to break calculation?

        double alpha3 = Math.acos( -Math.cos(alpha1)*Math.cos(alpha2) +
                             Math.sin(alpha1)*Math.sin(alpha2)*Math.cos(lamda12) );
        double lamda13 = Math.atan2(Math.sin(lamda12) * Math.sin(alpha1) * Math.sin(alpha2),
                Math.cos(alpha2) + Math.cos(alpha1) * Math.cos(alpha3));
        double fi3 = Math.asin(Math.sin(fi1) * Math.cos(lamda13) +
                Math.cos(fi1) * Math.sin(lamda13) * Math.cos(theta13));
        double delta_gamma13 = Math.atan2(Math.sin(theta13) * Math.sin(lamda13) * Math.cos(fi1),
                Math.cos(lamda13) - Math.sin(fi1) * Math.sin(fi3));
        double gamma3 = gamma1 + delta_gamma13;
        gamma3 = (gamma3+3*Math.PI) % (2*Math.PI) - Math.PI; // normalise to -180..+180�

        return new GlobalCoordinates(Angle.toDegrees(fi3), Angle.toDegrees(gamma3));
    }


    public static boolean intersection2d(double a1, double b1, double a2, double b2, double []intersection)
    {
        if (a1 == a2)
            return false;

        intersection[0] = (b2 - b1) / (a1 - a2);
        intersection[1] = a1 * intersection[0] + b1;

        return true;
    }


    // https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
    public static double distancePointToLine2d(double a, double b, double []xy)
    {
        // formula below is for ax + by + c = 0, in our case we work with y = ax + b
        // so converting it to the first representation we get ax - y + b = 0

        double div = Math.sqrt(Math.pow(a, 2) + 1);
        if (div == 0)
            div = 1e-10;

        return Math.abs(a * xy[0] - xy[1] + b) / div;
    }

    /*
    based on https://developers.google.com/maps/documentation/javascript/examples/map-projection-simple
    */
    public static void projectGeodeticTo2d(GlobalCoordinates geodetic, double []xy)
    {
        double latRadians = geodetic.getLatitude() * Math.PI / 180;
        xy[0] = GALL_PETERS_RANGE_X * (0.5 + geodetic.getLongitude() / 360);
        xy[1] = GALL_PETERS_RANGE_Y * (0.5 - 0.5 * Math.sin(latRadians));
    }

    public static GlobalCoordinates project2dToGeodetic(double []xy)
    {
        double x = xy[0] / GALL_PETERS_RANGE_X;
        double y = Math.max(0, Math.min(1, xy[1] / GALL_PETERS_RANGE_Y));

        return new GlobalCoordinates(Math.asin(1 - 2 * y) * 180 / Math.PI, -180 + 360 * x);
    }

    /*
    based on http://kartoweb.itc.nl/geometrics/map%20projections/body.htm
    */
    public static void projectMercatorGeodeticTo2d(GlobalCoordinates geodetic, double []xy)
    {
        double latRadians = geodetic.getLatitude() * Math.PI / 180;
        double longRadians = geodetic.getLongitude() * Math.PI / 180;
        xy[0] = EARTH_RADIUS * longRadians;
        xy[1] = EARTH_RADIUS * Math.log(Math.tan(Math.PI/4 + latRadians/2));
    }

    public static GlobalCoordinates projectMercator2dToGeodetic(double []xy)
    {
        double x = xy[0] / EARTH_RADIUS;
        double y = Math.PI/2 - 2 * Math.atan(Math.pow(Math.E, -xy[1] / EARTH_RADIUS));

        return new GlobalCoordinates(y / Math.PI * 180, x / Math.PI * 180);
    }

    /**
     * Converts ‘this’ point from (spherical geodetic) latitude/longitude coordinates to (geocentric) cartesian
     * (x/y/z) coordinates. To convert from ellipsoidal on specified datum - see original code in http://www.movable-type.co.uk/scripts/
     *
     * @returns {Vector3d} Vector pointing to lat/lon point, with x, y, z in metres from earth centre.
     */
    public static void toCartesian(GlobalCoordinates geodetic, double []xyz) {
        double fi = Angle.toRadians(geodetic.getLatitude()), gamma = Angle.toRadians(geodetic.getLongitude());
        double h = 0; // height above ellipsoid - not currently used
        double a = EARTH_RADIUS, b = EARTH_RADIUS; // elipsoid.a/b

        double sinfi = Math.sin(fi), cosfi = Math.cos(fi);
        double singamma = Math.sin(gamma), cosgamma = Math.cos(gamma);

        double eSq = (a*a - b*b) / (a*a); // zero in case of sphere
        double vi = a / Math.sqrt(1 - eSq*sinfi*sinfi);

        xyz[0] = (vi+h) * cosfi * cosgamma;
        xyz[1] = (vi+h) * cosfi * singamma;
        xyz[2] = ((1-eSq)*vi + h) * sinfi;
    }


    /**
     * Converts ‘this’ (geocentric) cartesian (x/y/z) point to (spherical geodetic) latitude/longitude
     * coordinates. To convert to ellipsoidal on specified datum - see original code in http://www.movable-type.co.uk/scripts/
     *
     * Uses Bowring’s (1985) formulation for µm precision.
     *
     * @param {LatLon.datum.transform} datum - Datum to use when converting point.
     */
    public static void toLatLon(double []xyz, GlobalCoordinates geodetic) {
        double a = EARTH_RADIUS, b = EARTH_RADIUS; // datum.ellipsoid.a, b = datum.ellipsoid.b;

        double e2 = (a*a-b*b) / (a*a); // 1st eccentricity squared
        double etta2 = (a*a-b*b) / (b*b); // 2nd eccentricity squared
        double p = Math.sqrt(xyz[0]*xyz[0] + xyz[1]*xyz[1]); // distance from minor axis
        double R = Math.sqrt(p*p + xyz[2]*xyz[2]); // polar radius

        // parametric latitude (Bowring eqn 17, replacing tanß = z·a / p·b)
        double tanb = (b*xyz[2])/(a*p) * (1+etta2*b/R);
        double sinb = tanb / Math.sqrt(1+tanb*tanb);
        double cosb = sinb / tanb;

        // geodetic latitude (Bowring eqn 18)
        double fi = Math.atan2(xyz[2] + etta2*b*sinb*sinb*sinb, p - e2*a*cosb*cosb*cosb);

        // longitude
        double gamma = Math.atan2(xyz[1], xyz[0]);

        // height above ellipsoid (Bowring eqn 7) [not currently used]
        double sinfi = Math.sin(fi), cosf = Math.cos(fi);
        double vi = a*Math.sqrt(1-e2*sinfi*sinfi); // length of the normal terminated by the minor axis
        double h = p*cosf + xyz[2]*sinfi - (a * a / vi);

         geodetic.setLatitude(Angle.toDegrees(fi));
        geodetic.setLongitude(Angle.toDegrees(gamma));
    }

}

