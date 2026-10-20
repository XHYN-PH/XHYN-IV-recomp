package com.libertyrecomp;
public class DriverPolicyTest {
 public static void main(String[] args) {
  assert DriverPolicy.automatic("Adreno (TM) 650",29).equals("turnip");
  assert DriverPolicy.automatic("Adreno (TM) 750",35).equals("turnip");
  assert DriverPolicy.automatic("Adreno 830",35).equals("system");
  assert DriverPolicy.automatic("Mali-G715",35).equals("system");
  assert DriverPolicy.supports("panvk","Immortalis-G720",35);
  assert !DriverPolicy.supports("panvk","Mali-G715",34);
  assert !DriverPolicy.supports("panvk","Mali-G52",35);
  assert !DriverPolicy.supports("panvk","Mali-G625",35);
  assert DriverPolicy.supports("radv","Samsung Xclipse 940",34);
  assert !DriverPolicy.supports("radv","Samsung Xclipse 950",35);
  assert !DriverPolicy.supports("radv","Samsung Xclipse 940",33);
  assert !DriverPolicy.supports("turnip",null,35);
  assert !DriverPolicy.supports("turnip","Adreno 7500",35);
  System.out.println("Driver admission regression checks passed");
 }
}
