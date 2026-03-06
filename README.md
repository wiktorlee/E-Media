# Analizator Plików WAV - Projekt E-Media

Aplikacja desktopowa w Javie służąca do niskopoziomowej analizy binarnej, wizualizacji i anonimizacji plików audio w formacie WAV.

## Główne Założenia
- Ręczny odczyt metadanych (analiza bajt po bajcie).
- Wyświetlanie atrybutów: rozmiar, częstotliwość próbkowania, liczba kanałów.
- Analiza częstotliwościowa z wykorzystaniem transformacji Fouriera.
- Anonimizacja pliku bez ingerencji w dźwięk.

## Plan Implementacji

### Etap 1 (Ocena 3.0)
- **Parser:** Manualny odczyt zawartości niezbędnej do odtworzenia dźwięku (nagłówek, chunk WAVE/fmt).
- **Widmo:** Wyświetlenie wykresu widma pliku za pomocą transformacji Fouriera.
- **Anonimizacja:** Czyszczenie dodatkowych segmentów (RIFF chunks).
- **GUI:** Prezentacja pliku (odtwarzanie, wizualizacja fali) oraz jego atrybutów.

### Etap 2 (Ocena 4.0)
- **Metadane:** Wczytanie i wyświetlenie zawartości dużego chunka z metadanymi (np. INFO RIFF chunk).
- **Anonimizacja+:** Rozszerzenie procesu o czyszczenie zawartości samego WAVE chunk.
- **Testowanie:** Propozycja i opis sposobu testowania poprawności działania transformacji FFT.

### Etap 3 (Ocena 5.0)
- **Metadane+:** Obsługa dodatkowych metod zapisu metadanych (np. standard XMP).
- **Analiza:** Badanie możliwości zakodowania informacji przy użyciu różnych metod zapisu tych samych danych audio.
- **Spektrogram:** Implementacja rysowania spektrogramu wraz z uzasadnieniem wyboru funkcji okienka (np. Hamming, Hann).
