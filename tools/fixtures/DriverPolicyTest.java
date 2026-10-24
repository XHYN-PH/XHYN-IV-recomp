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
  assert DriverPolicy.supports("radv18","Samsung Xclipse 960",34);
  assert !DriverPolicy.supports("radv18","Samsung Xclipse 960",33);
  assert DriverPolicy.supports("turnip_v38","Adreno 710",29);
  assert !DriverPolicy.supports("turnip_v38","Adreno 540",35);
  for(int model:new int[]{613,618,630,650,660,680,690,710,720,722,725,730,735,740,750,810,825,829,830,840})
   assert DriverPolicy.supports("turnip_v43","Adreno (TM) "+model,35):model;
  assert !DriverPolicy.supports("turnip_v43","Mali-G720",35);
  assert !DriverPolicy.supports("turnip_v43","Adreno 7100",35);
  assert !DriverPolicy.supports("turnip_v43","Adreno 710",28);
  assert DriverPolicy.reason("panvk","Mali-G715",34).contains("Android 15");
  assert DriverPolicy.reason("radv","Xclipse 950",35).contains("does not admit");
  System.out.println("Driver admission regression checks passed");
 }
}
