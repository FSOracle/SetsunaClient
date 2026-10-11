package Combat.Killaura.core;

@FunctionalInterface
public interface SettingChangeListener<T> {
    boolean beforeChange(Setting<T> setting, T oldValue, T newValue);
}
