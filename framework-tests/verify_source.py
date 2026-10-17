from pathlib import Path
import hashlib
root=Path(__file__).resolve().parent
repo=root.parent/'vendor/react-native-screens'
fixture=(root/'src/test/java/review/flow/FlowFixture.kt').read_text()
def method(text,name):
 start=text.index(name);left=text.index('{',start);depth=1;end=left+1
 while depth:
  depth+=(text[end]=='{')-(text[end]=='}');end+=1
 return text[start:end]
checks=[('Screen.kt',n) for n in ['fun markAsBeingRemoved()', 'fun startRemovalTransition()', 'fun endRemovalTransition()', 'private fun startTransitionRecursive(parent: ViewGroup?)']]
checks += [('ScreenStack.kt','override fun endViewTransition(view: View)'),('ScreenStackHeaderConfig.kt','private fun isInRemovalTransition()')]
for file,name in checks:
 source=(repo/'android/src/main/java/com/swmansion/rnscreens/legacy'/file).read_text()
 actual=method(source,name);copied=method(fixture,name)
 assert actual==copied,(file,name)
 print('EXACT',file,name,hashlib.sha256(actual.encode()).hexdigest())
print('LIMIT: Header onUpdate is a reduced fixture; RN/Fragment/Tabs lifecycle is omitted.')
