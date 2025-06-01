package org.example.frontend.cipher.utils;

import java.util.ArrayList;
import java.util.List;

public interface GaloisOperations {
    List<Byte> irrediciblePoly8Degree = irrediciblePolynoms8Degree();

    static byte addPolys(byte a, byte b) {
        return (byte) (a ^ b);
    }

    static byte multOnX(byte number, byte mod) {
        if (!isByteIrredicible(mod)) throw new IllegalArgumentException("mod must be irredicible");

        if ((number & 0x80) ==  0x80) {
            return (byte) ((number << 1) ^ (mod & 0xFF) );
        }
        return (byte) (number << 1);
    }

    static byte multiplyPolymomsByMod(byte pol, byte a, byte mod) {
        if (!isByteIrredicible(mod)) throw new IllegalArgumentException("mod must be irredicible");
        byte result = 0;
        for (int i = 0; i < 8; i++) {
            if ((a & 0x01) == 0x01) {
                result ^= pol;
            }
            pol = multOnX(pol, mod);
            a >>>= 1;
        }
        return result;
    }

    static byte powMod(byte polynom, int degree, byte mod) {
        if (!isByteIrredicible(mod)) throw new IllegalArgumentException("mod must be irredicible");
        if (degree < 0) throw new IllegalArgumentException("degree must be >= 0");
        byte result = 1;
        while (degree != 0) {
            if ((degree & 1) == 1) {
                result = multiplyPolymomsByMod(result, polynom, mod);
            }
            polynom = multiplyPolymomsByMod(polynom, polynom, mod);
            degree >>>= 1;
        }
        return result;
    }

    static byte getInversePolynom(byte polynom, byte mod) {
        if (!isByteIrredicible(mod)) throw new IllegalArgumentException("mod must be irredicible");
        return powMod(polynom, 254, mod);
    }

    static int degree(short poly) {
        if (poly == 0) return -1;
        int degree = 0;
        int tmp = poly & 0xFFFF;
        while (tmp != 0) {
            degree++;
            tmp >>>= 1;
        }
        return degree-1;
    }

    static short[] divideAndMod(short a, short b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        int degreeSecond = degree(b);
        short full  = 0;
        short mod = a;
        while (degree(mod) >= degreeSecond) {
            int shift = degree(mod) - degreeSecond;
            full ^= (short) (1 << shift);
            mod ^= (short) (b << shift);
        }
        return new short[]{full, mod};
    }

    static short divide(short a, short b) {
        return divideAndMod(a, b)[0];
    }

    static short mod(short a, short b) {
        return divideAndMod(a, b)[1];
    }

    static boolean isIrredicible(short pol, int degree) {
        int upperValue = 1 << (degree/2 + 1);
        for (int i = 2; i < upperValue; i++) {
            if (mod(pol, (short) i) == 0) {
                return false;
            }
        }
        return true;
    }

    static List<Short> calculateAllIrrediciblePolynoms(int degree) {
        List<Short> result = new ArrayList<>();
        short start = (short) (1 << degree);
        short end = (short) (start << 1);
        for (short i = start; i < end; i++) {
            if (isIrredicible(i, degree)) {
                result.add(i);
            }
        }
        return result;
    }

    static List<Byte> irrediciblePolynoms8Degree() {
        List<Short> polynoms = calculateAllIrrediciblePolynoms(8);
        List<Byte> result = new ArrayList<>();
        for (Short polynom : polynoms) {
            result.add((byte) (polynom & 0xFF));
        }
        return result;
    }

    static boolean isByteIrredicible(byte poly) {
        return irrediciblePoly8Degree.contains(poly);
    }

    static List<Short> factorizePolynomial(short polynomial) {
        List<Short> factors = new ArrayList<>();

        if (polynomial == 0) {
            return factors;
        }

        int deg = degree(polynomial);
        if (deg == 0) {
            factors.add((short) 1);
            return factors;
        }

        if (isIrredicible(polynomial, deg)) {
            factors.add(polynomial);
            return factors;
        }

        List<Short> possibleFactors = new ArrayList<>();
        for (int d = 1; d <= deg/2; d++) {
            possibleFactors.addAll(calculateAllIrrediciblePolynoms(d));
        }

        for (Short factor : possibleFactors) {
            while (mod(polynomial, factor) == 0) {

                factors.add(factor);
                polynomial = divide(polynomial, factor);
                deg = degree(polynomial);

                if (polynomial == 1) {
                    return factors;
                }

                if (isIrredicible(polynomial, deg)) {
                    factors.add(polynomial);
                    return factors;
                }
            }
        }
        if (polynomial != 1) {//если вышло что-то что не равно 1 то это что-то само является неприводимым
            factors.add(polynomial);
        }
        return factors;
    }




}
