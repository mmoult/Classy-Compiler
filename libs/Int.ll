; Declare the string that we will use for printing the number
@.str = private unnamed_addr global [13 x i8] c"\00\00\00\00\00\00\00\00\00\00\00\00\00"

; Function Attrs: noinline nounwind optnone uwtable
define dso_local void @printi(i32 %0) {
  %num = alloca i32, align 4
  store i32 %0, i32* %num, align 4
  %offs = alloca i32, align 4
  store i32 0, i32* %offs, align 4
  ; Set the negative sign, if there should be one
  %neg = icmp slt i32 %0, 0
  br i1 %neg, label %thenneg, label %nextneg
thenneg:
  %pos = mul nsw i32 %0, -1
  store i32 %pos, i32* %num, align 4
  %offs1 = load i32, i32* %offs, align 4
  %negsign = getelementptr inbounds [13 x i8], [13 x i8]* @.str, i64 0, i32 %offs1
  store i8 45, i8* %negsign, align 1
  %nextoffs = add nsw i32 %offs1, 1
  store i32 %nextoffs, i32* %offs, align 4
  br label %nextneg
nextneg:
  ; We want to find out how many digits it will take to print out number
  %num1 = load i32, i32* %num, align 4
  %dupnum = alloca i32, align 4
  store i32 %num1, i32* %dupnum, align 4
  %digits = alloca i32, align 4
  store i32 1, i32* %digits, align 4
  br label %compdigits
compdigits:
  %dupnum1 = load i32, i32* %dupnum, align 4
  %is0 = icmp slt i32 %dupnum1, 10
  br i1 %is0, label %afterdigits, label %fordigits
fordigits:
  %tenth = sdiv i32 %dupnum1, 10
  store i32 %tenth, i32* %dupnum, align 4
  %digits1 = load i32, i32* %digits, align 4
  %adddigits = add nsw i32 %digits1, 1
  store i32 %adddigits, i32* %digits, align 4
  br label %compdigits
afterdigits:
  ; With the number of digits, we can start setting the actual number in string
  %digits2 = load i32, i32* %digits, align 4
  %offs2 = load i32, i32* %offs, align 4
  %adddig = add nsw i32 %digits2, %offs2
  %lastdig = sub nsw i32 %adddig, 1
  store i32 0, i32* %offs, align 4; Reset offs to 0, since we go backwards
  br label %fornum
fornum:
  %num2 = load i32, i32* %num, align 4
  %dig = srem i32 %num2, 10
  %digb = trunc i32 %dig to i8
  %offs3 = load i32, i32* %offs, align 4
  %at = sub nsw i32 %lastdig, %offs3
  %digchar = add nsw i8 %digb, 48
  %numat = getelementptr inbounds [13 x i8], [13 x i8]* @.str, i64 0, i32 %at
  store i8 %digchar, i8* %numat, align 1
  ; increase offset
  %offsadd = add nsw i32 %offs3, 1
  store i32 %offsadd, i32* %offs, align 4
  %numdiv = sdiv i32 %num2, 10
  store i32 %numdiv, i32* %num, align 4
  %numzero = icmp eq i32 %numdiv, 0
  br i1 %numzero, label %afternum, label %fornum
afternum:
  ; Convert [13 x i8]* to i8*...
  %string = getelementptr [13 x i8], [13 x i8]* @.str, i64 0, i64 0
  ; Call puts function to write out the string to stdout.
  call i32 @puts(i8* %string)
  ret void
}

FUNCTION ..print {
  %1 = bitcast i8* %int to %Int*
  %2 = getelementptr inbounds %Int, %Int* %1, i32 0, i32 1
  %3 = load i32, i32* %2, align 4
  call void @printi(i32 %3)
  ret void
}