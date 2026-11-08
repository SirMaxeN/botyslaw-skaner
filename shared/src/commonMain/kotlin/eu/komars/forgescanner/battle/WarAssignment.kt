package eu.komars.forgescanner.battle
/** Maximum expected wins across the clan, with dummy columns for unfavourable/unknown matchups. */
object WarAssignment {
 fun best(probabilities:Array<DoubleArray>):IntArray {
  val n=probabilities.size;if(n==0)return IntArray(0);val targets=probabilities[0].size
  require(probabilities.all {it.size==targets&&it.all {p->p.isNaN()||p in 0.0..1.0}})
  val m=targets+n;val u=DoubleArray(n+1);val v=DoubleArray(m+1);val p=IntArray(m+1);val way=IntArray(m+1)
  for(i in 1..n) {p[0]=i;var j0=0;val min=DoubleArray(m+1){Double.POSITIVE_INFINITY};val used=BooleanArray(m+1)
   do {used[j0]=true;val i0=p[j0];var delta=Double.POSITIVE_INFINITY;var j1=0
    for(j in 1..m)if(!used[j]) {val value=if(j<=targets)probabilities[i0-1][j-1].takeIf {it>.5}?:0.0 else 0.0;val cur=-value-u[i0]-v[j]
     if(cur<min[j]){min[j]=cur;way[j]=j0};if(min[j]<delta){delta=min[j];j1=j}}
    for(j in 0..m)if(used[j]){u[p[j]]+=delta;v[j]-=delta}else min[j]-=delta
    j0=j1
   }while(p[j0]!=0)
   do {val j1=way[j0];p[j0]=p[j1];j0=j1}while(j0!=0)
  }
  val answer=IntArray(n){-1};for(j in 1..targets)if(p[j]>0&&probabilities[p[j]-1][j-1]>.5)answer[p[j]-1]=j-1
  return answer
 }
}
