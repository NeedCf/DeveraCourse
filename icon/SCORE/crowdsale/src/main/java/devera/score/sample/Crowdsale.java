package devera.score.example;


import devera.score.sample.Student;

import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Payable;

import java.math.BigInteger;

public class Crowdsale
{
    private static final BigInteger ONE_ICX = new BigInteger("1000000000000000000");

    private final long deadline;
    private final DictDB<Address, BigInteger> balances;
    private final VarDB<BigInteger> amountRaised;

    private final Address teacherAddress;
    private final BigInteger tuition;
    private BigInteger testAmount;
    private Boolean activeCourse;
    private final BigInteger numberOfLesson;
    private BigInteger currentNumberOfLesson;
    private final ArrayDB<Student> listStudent = Context.newArrayDB("listStudent", Student.class);;
    private final DictDB<Student, Boolean> check;

    public Crowdsale(BigInteger _tuition, BigInteger _numberOfLesson, BigInteger _durationInDefault) {
        // some basic requirements
        Context.require(_tuition.compareTo(BigInteger.ZERO) >= 0);
        Context.require(_numberOfLesson.compareTo(BigInteger.ZERO) >= 0);

        this.teacherAddress = Context.getCaller();
        this.tuition = ONE_ICX.multiply(_tuition);
        this.numberOfLesson = _numberOfLesson;
        this.currentNumberOfLesson = BigInteger.ZERO;
        this.deadline = Context.getBlockHeight() + _durationInDefault.longValue();
        this.activeCourse = false;

        this.balances = Context.newDictDB("balances", BigInteger.class);
        this.amountRaised = Context.newVarDB("amountRaised", BigInteger.class);
        this.check = Context.newDictDB("check", Boolean.class);
    }

    @External(readonly=true)
    public String name() {
        return "Devera Course";
    }

    @External(readonly=true)
    public String description() {
        return "Blockchain course from Devera";
    }

    @External(readonly=true)
    public BigInteger balanceOf(Address _owner) {
        return this.safeGetBalance(_owner);
    }

    @External(readonly=true)
    public BigInteger CurrentLesson() {
        return this.currentNumberOfLesson;
    }

    @External(readonly=true)
    public BigInteger amountRaised() {
        return safeGetAmountRaised();
    }

    @External(readonly=true)
    public BigInteger TotalNumberOfLesson() {
        
        return this.numberOfLesson;
    }

    @External(readonly=true)
    public BigInteger checkNumberOfStudentAttended(Address _owner) {
        
        return BigInteger.valueOf(this.listStudent.size());
    }

    @External(readonly=true)
    public String isOpenRollCall() {
        
        return this.activeCourse.toString();
    }

    private Boolean afterEndCourse() {
        
        return this.currentNumberOfLesson.compareTo(this.numberOfLesson) >= 0;
    }

    /*
     * Called when anyone sends funds to the SCORE and that funds would be regarded as a contribution.
     */
    @Payable
    public void fallback() {
        // check if the course is ended
        Context.require(!this.afterEndCourse());

        Address _from = Context.getCaller();
        Context.require(!_from.equals(this.teacherAddress));
        BigInteger _value = Context.getValue();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0);

        //check tuition
        Context.require(_value.compareTo(this.tuition) >= 0);

        // accept the tuition
        BigInteger fromBalance = safeGetBalance(_from);
        if (fromBalance.compareTo(BigInteger.ZERO) <= 0) {
            Student newStudent = new Student(_from, BigInteger.valueOf(0), _value);
            //add student address to list student
            this.listStudent.add( newStudent);
        }
             
        this.balances.set(_from, fromBalance.add(_value));
    
        BigInteger amountRaised = safeGetAmountRaised();
        this.amountRaised.set(amountRaised.add(_value));

        // emit eventlog
        Registration(_from, _value);
    }

    private Boolean isAvailableToRefund(Address _from){
        //counter
        BigInteger _studentCounter = this.safeGetCounter(_from);
        if (_studentCounter.compareTo(BigInteger.ZERO) <= 0)
            return false;

        long _require = this.numberOfLesson.longValue();
        double _tmp = (double)(_require * 0.8); 
        _tmp = StrictMath.ceil(_tmp);
        _require = StrictMath.round(_tmp);
        BigInteger _requireLesson = BigInteger.valueOf(_require);
        //balance 
        BigInteger _studentTuition = this.safeGetBalance(_from);
        Boolean _checkBalance = (_studentTuition.compareTo(BigInteger.ZERO) > 0);
        return ((_studentCounter.compareTo(_requireLesson) >= 0) && _checkBalance);
    }

    private void refund(Address _from, BigInteger _value){
        this.amountRaised.set(this.amountRaised.get().subtract(_value));
        this.balances.set(_from,BigInteger.ZERO);
        Context.transfer(_from,_value);
    }

    @External
    public void withdraw() {
        // only withdraw when the course is finished
        Context.require(this.afterEndCourse());
        Address _from = Context.getCaller();
        //if caller is teacher
        if (_from.equals(this.teacherAddress) ){
            
            // refund tuition to student first
            // if not available, delete balance
            for (int i = 0; i < this.listStudent.size() ; i++){
                if (isAvailableToRefund(this.listStudent.get(i).getStudentAddress())){
                    Address _studentAddress = this.listStudent.get(i).getStudentAddress();
                    BigInteger _value = this.safeGetBalance(_studentAddress);
                    Context.require(_value.compareTo(BigInteger.ZERO) > 0);

                    refund(_studentAddress, _value);
                }
                this.balances.set(this.listStudent.get(i).getStudentAddress(),BigInteger.ZERO);
            }
            //refund to teacher
            BigInteger _amount = this.amountRaised.get();
            this.amountRaised.set(BigInteger.ZERO);
            Context.transfer(this.teacherAddress, _amount);
            return;
        }

        //check existed student
        BigInteger _value = this.safeGetBalance(_from);
        Context.require(_value.compareTo(BigInteger.ZERO) > 0);

        //check requirement number of class
        Context.require(isAvailableToRefund(_from));

        //refund to student
        refund(_from, _value);
   
    }

    @External
    public void rollCall() {
        Address _from = Context.getCaller();
        // check if it is in the time of roll call
        Student _student = studentGetByAddress(_from);
        if (this.activeCourse)
            if (!afterDeadline()){
                BigInteger tmp_num = this.safeGetCounter(_from);
                _student.setCount(tmp_num.add(BigInteger.ONE));
            }     
            else {
                this.activeCourse = false;
        } 
    }

    @External
    public void openRollCall() {
        Address _from = Context.getCaller();
        Context.require(this.teacherAddress.equals(_from));
        Context.require(!this.activeCourse);
        //create new list roll call
        for (int i = 0; i < this.listStudent.size() ; i++)
            this.check.set(this.listStudent.get(i),false);
        
        //check end of course
        if (!afterEndCourse()){
            this.activeCourse = true;
            BigInteger one_big =  new BigInteger("1");
            this.currentNumberOfLesson = this.currentNumberOfLesson.add(one_big);
            //even log
            ActiveCourse(_from,this.currentNumberOfLesson);
            return;
        }
        
    }
    @External
    public void closeRollCall() {
        Address _from = Context.getCaller();
        Context.require(this.teacherAddress.equals(_from));
        Context.require(this.activeCourse);
        
        this.activeCourse = false;
        
      
        //even log
        InActiveCourse(_from,this.currentNumberOfLesson);
    }

    private BigInteger safeGetBalance(Address owner) {
        return this.balances.getOrDefault(owner, BigInteger.ZERO);
    }

    private BigInteger safeGetAmountRaised() {
        return this.amountRaised.getOrDefault(BigInteger.ZERO);
    }

    private BigInteger safeGetCounter(Address owner) {
        for (int i = 0; i < this.listStudent.size(); i++) {
            if (this.listStudent.get(i).getStudentAddress().toString().compareTo(owner.toString()) == 0) {
                return BigInteger.valueOf(i);
            }
        }
        return BigInteger.valueOf(-1);
    }

    private Student studentGetByAddress(Address owner) {
        for (int i = 0; i < this.listStudent.size(); i++) {
            if (this.listStudent.get(i).getStudentAddress().toString().compareTo(owner.toString()) == 0) {
                return this.listStudent.get(i);
            }
        }
        return null;
    }

    private boolean afterDeadline() {
        // checks if it has been reached to the deadline block
        return Context.getBlockHeight() >= this.deadline;
    }




    @EventLog(indexed=2)
    public void FundDeposit(Address backer, BigInteger amount) {}

    @EventLog(indexed=2)
    protected void Registration(Address _from,BigInteger _value){};

    @EventLog(indexed=2)
    protected void FundWithdraw(Address owner, BigInteger amount) {}

    @EventLog(indexed=2)
    protected void ActiveCourse(Address _from,BigInteger _currentNumberOfLesson){};

    @EventLog(indexed=2)
    protected void InActiveCourse(Address _from,BigInteger _currentNumberOfLesson){};
}
