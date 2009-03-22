lambda {|a,b,c|
  withTransaction {
    q "select ?", a + b + c
    #raise "kinikuwanai"
  }
}
