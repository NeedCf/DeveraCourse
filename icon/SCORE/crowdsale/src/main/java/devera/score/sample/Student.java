package devera.score.sample;

import score.Address;
import java.math.BigInteger;
import score.ObjectReader;
import score.ObjectWriter;
import java.util.Map;

public class Student {
    private Address studentAddress;
    private BigInteger attendance;
    private BigInteger tuitionFee;

    public Student(Address studentAddress, BigInteger attendance, BigInteger tuitionFee) {
        this.studentAddress = studentAddress;
        this.attendance = attendance;
        this.tuitionFee = tuitionFee;
    }

    public Address getStudentAddress() {
        return this.studentAddress;
    }

    public BigInteger getAttendance() {
        return this.attendance;
    }

    public BigInteger getTuitionFee() {
        return this.tuitionFee;
    }

    public void setCount(BigInteger count) {
        this.attendance = count;
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "_studentAddress", this.studentAddress,
                "_attendance", this.attendance.toString(),
                "_tuitionFee", this.tuitionFee.toString()
        );
    }

    public static void writeObject(ObjectWriter w, Student s) {
        w.beginList(3);
        w.write(s.getStudentAddress());
        w.write(s.getAttendance());
        w.write(s.getTuitionFee());   
        w.end();
    }
    
    public static Student readObject(ObjectReader r) {
        r.beginList();
        Address address = r.readAddress();
        BigInteger attendance = r.readBigInteger();
        BigInteger tuition = r.readBigInteger();
        
        
        return new Student(address, attendance, tuition);
    }
}
