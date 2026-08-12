/**
 * @format
 */

import { AppRegistry } from 'react-native';
import App from './App';
import { fogNode } from './src/fog/fogNode';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);
AppRegistry.registerHeadlessTask('AnxietyWatchFogSync', () => async () => {
  await fogNode.runOnce();
});
