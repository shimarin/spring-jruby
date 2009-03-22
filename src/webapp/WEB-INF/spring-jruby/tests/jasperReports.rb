getResource("WEB-INF/spring-jruby/tests/FirstJasper.jrxml") \
  .compileJasperDesign \
  .fillReport({:MaxOrderID=>100000.to_java_int,:ReportTitle=>"hogehoge report"}) \
   .export \
   .download("application/pdf")