@TRUE = private unnamed_addr global [5 x i8] c"true\00"
@FALSE = private unnamed_addr global [6 x i8] c"false\00"

FUNCTION ..print {
  %1 = bitcast i8* %bool to %Bool*
  %2 = getelementptr inbounds %Bool, %Bool* %1, i32 0, i32 1
  %3 = load i1, i1* %2, align 4
  br i1 %3, label %ifTrue, label %ifFalse
ifTrue:
  %ts = getelementptr [5 x i8], [5 x i8]* @TRUE, i64 0, i64 0
  call i32 @puts(i8* %ts)
  ret void
ifFalse:
  %fs = getelementptr [6 x i8], [6 x i8]* @FALSE, i64 0, i64 0
  call i32 @puts(i8* %fs)
  ret void
}