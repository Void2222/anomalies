package net.void_.anomalies.client.gui.editor.model;

public enum NodeDiffStatus {
    CLEAN,       // Не изменено (совпадает с диском/миром) - серый
    MODIFIED,    // Изменены параметры/свойства - синий
    ADDED,       // Новое состояние - зеленый
    DELETED      // Помечено на удаление - красный
}
