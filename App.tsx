import React, {useState} from 'react';
import {Button, SafeAreaView, Text, View} from 'react-native';
import NestedStacks from './R1';
import NativeTabs from './R2';
import MovedScreens from './R3';

export default function App() {
  const [scenario, setScenario] = useState<'menu' | 'stacks' | 'tabs' | 'moved' | 'moved-nested'>('menu');
  if (scenario === 'stacks') {
    return <NestedStacks />;
  }
  if (scenario === 'tabs') {
    return <NativeTabs />;
  }
  if (scenario === 'moved' || scenario === 'moved-nested') {
    return <MovedScreens nested={scenario === 'moved-nested'} />;
  }
  return (
    <SafeAreaView style={{flex: 1, justifyContent: 'center', padding: 24}}>
      <Text>Header removal comparison</Text>
      <View style={{height: 20}} />
      <Button title="Nested stack and two Back events" onPress={() => setScenario('stacks')} />
      <Button title="Native tab removal and remount" onPress={() => setScenario('tabs')} />
      <Button title="Move existing screen to top" onPress={() => setScenario('moved')} />
      <Button title="Move screen with nested header" onPress={() => setScenario('moved-nested')} />
    </SafeAreaView>
  );
}
